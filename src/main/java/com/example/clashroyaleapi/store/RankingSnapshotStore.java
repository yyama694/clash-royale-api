package com.example.clashroyaleapi.store;

import com.example.clashroyaleapi.domain.RankingSnapshot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * ランキングの記録({@link RankingSnapshot})の保存先。1回の記録を1ファイルにし、日付(UTC)ごとのディレクトリに置く
 * (`2026-10-06/091500.tsv`。ファイル名は記録日時の時分秒なので、記録日時は秒単位にしておく)。
 * 1行目が「記録日時(ISO形式) \t 終わったシーズンのうち最新のもの(無ければ空)」、2行目以降が順位の順に
 * 「順位 \t タグ \t レーティング \t 名前」。名前はプレイヤーが自由に付けるので、タブ・改行を空白に置き換える。
 * 公式APIは今のランキングしか返さず、過去の記録は作り直せないので、古いものも消さない。
 */
public class RankingSnapshotStore {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss").withZone(ZoneOffset.UTC);
    private static final String SUFFIX = ".tsv";

    /** 記録の一覧に使う、ファイルの先頭(記録日時・シーズン・1位)だけを読んだもの。 */
    public record Summary(Instant takenAt, String finishedSeason, RankingSnapshot.Entry leader) {
    }

    private final Path dir;

    public RankingSnapshotStore(Path dir) {
        this.dir = dir;
    }

    public void write(RankingSnapshot snapshot) throws IOException {
        Path file = fileOf(snapshot.takenAt());
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            writer.write(snapshot.takenAt().toString());
            writer.write('\t');
            writer.write(snapshot.finishedSeason() == null ? "" : snapshot.finishedSeason());
            writer.write('\n');
            for (RankingSnapshot.Entry entry : snapshot.entries()) {
                writer.write(String.join("\t", String.valueOf(entry.rank()), entry.tag(),
                        String.valueOf(entry.rating()), entry.name().replaceAll("[\\t\\r\\n]", " ")));
                writer.write('\n');
            }
        }
        // 書きかけのファイルを読まないよう、書き終えてから差し替える。
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** その日時の記録。無い・読めない形式なら空。 */
    public Optional<RankingSnapshot> read(Instant takenAt) throws IOException {
        Path file = fileOf(takenAt);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Optional<Header> header = parseHeader(reader.readLine());
            if (header.isEmpty()) {
                return Optional.empty();
            }
            List<RankingSnapshot.Entry> entries = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    entries.add(parseEntry(line));
                }
            }
            return Optional.of(new RankingSnapshot(header.get().takenAt(), header.get().finishedSeason(), entries));
        } catch (DateTimeException | IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            return Optional.empty();
        }
    }

    /** すべての記録の先頭だけを、古い順に。読めないファイルと1人もいない記録は飛ばす。 */
    public List<Summary> summaries() throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Path> files;
        try (Stream<Path> days = Files.list(dir)) {
            files = new ArrayList<>();
            for (Path day : days.filter(Files::isDirectory).toList()) {
                try (Stream<Path> inDay = Files.list(day)) {
                    inDay.filter(file -> file.getFileName().toString().endsWith(SUFFIX)).forEach(files::add);
                }
            }
        }
        List<Summary> summaries = new ArrayList<>();
        for (Path file : files) {
            readSummary(file).ifPresent(summaries::add);
        }
        summaries.sort(Comparator.comparing(Summary::takenAt));
        return summaries;
    }

    private Optional<Summary> readSummary(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Optional<Header> header = parseHeader(reader.readLine());
            String first = reader.readLine();
            if (header.isEmpty() || first == null || first.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new Summary(header.get().takenAt(), header.get().finishedSeason(), parseEntry(first)));
        } catch (DateTimeException | IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            return Optional.empty();
        }
    }

    private Path fileOf(Instant takenAt) {
        return dir.resolve(DAY.format(takenAt)).resolve(TIME.format(takenAt) + SUFFIX);
    }

    private record Header(Instant takenAt, String finishedSeason) {
    }

    private static Optional<Header> parseHeader(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }
        String[] columns = line.split("\t", -1);
        String season = columns.length > 1 && !columns[1].isBlank() ? columns[1] : null;
        return Optional.of(new Header(Instant.parse(columns[0].strip()), season));
    }

    private static RankingSnapshot.Entry parseEntry(String line) {
        String[] columns = line.split("\t", 4);
        return new RankingSnapshot.Entry(Integer.parseInt(columns[0]), columns[1], columns[3],
                Integer.parseInt(columns[2]));
    }
}
