package com.example.clashroyaleapi.store;

import com.example.clashroyaleapi.domain.TopBattle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * {@link TopBattle} の保存先。対戦の日付(UTC)ごとに <dir>/<日付>.tsv の1ファイルで、1行が1戦
 * 「時刻(ISO形式) \t タグ \t カードID(カンマ区切り) \t クラウン数 \t 消費漏れ」の後に、もう1人の同じ4列。
 * 対戦履歴は1人約30戦(上位勢で約1週間分)を返し、毎日の集計で同じ対戦が何度も取れるので、同じ対戦({@link TopBattle#key()})は1行にまとめる。
 * 公式APIは過去の対戦を後から返さないので、古い日のファイルも消さずに残す(1日約0.5MBの見込み)。
 */
public class TopBattleStore {

    private final Path dir;

    public TopBattleStore(Path dir) {
        this.dir = dir;
    }

    /** 対戦を日付ごとのファイルに足す。すでにある対戦は置き換える(中身は同じ)。 */
    public void add(Collection<TopBattle> battles) throws IOException {
        Map<LocalDate, List<TopBattle>> byDay = new TreeMap<>();
        for (TopBattle battle : battles) {
            byDay.computeIfAbsent(dayOf(battle.time()), day -> new ArrayList<>()).add(battle);
        }
        Files.createDirectories(dir);
        for (Map.Entry<LocalDate, List<TopBattle>> entry : byDay.entrySet()) {
            Path file = fileOf(entry.getKey());
            Map<String, TopBattle> merged = new LinkedHashMap<>();
            for (TopBattle battle : read(file)) {
                merged.put(battle.key(), battle);
            }
            for (TopBattle battle : entry.getValue()) {
                merged.put(battle.key(), battle);
            }
            write(file, merged.values().stream().sorted(Comparator.comparing(TopBattle::time)).toList());
        }
    }

    /** その時刻以降の対戦。ファイルが無ければ空。 */
    public List<TopBattle> readSince(Instant since) throws IOException {
        if (!Files.exists(dir)) {
            return List.of();
        }
        LocalDate firstDay = dayOf(since);
        List<Path> files;
        try (Stream<Path> listed = Files.list(dir)) {
            files = listed.filter(file -> dayOfFile(file) != null && !dayOfFile(file).isBefore(firstDay))
                    .sorted()
                    .toList();
        }
        List<TopBattle> battles = new ArrayList<>();
        for (Path file : files) {
            read(file).stream().filter(battle -> !battle.time().isBefore(since)).forEach(battles::add);
        }
        return battles;
    }

    private Path fileOf(LocalDate day) {
        return dir.resolve(day + ".tsv");
    }

    private static LocalDate dayOfFile(Path file) {
        String name = file.getFileName().toString();
        if (!name.endsWith(".tsv")) {
            return null;
        }
        try {
            return LocalDate.parse(name.substring(0, name.length() - ".tsv".length()));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalDate dayOf(Instant time) {
        return LocalDate.ofInstant(time, ZoneOffset.UTC);
    }

    /** 読めない行は飛ばす(1行の破損で、その日の全部を失わないため)。 */
    private static List<TopBattle> read(Path file) throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        List<TopBattle> battles = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    battles.add(parse(line));
                } catch (DateTimeException | IllegalArgumentException | IndexOutOfBoundsException ignored) {
                }
            }
        }
        return battles;
    }

    private static void write(Path file, List<TopBattle> battles) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            for (TopBattle battle : battles) {
                writer.write(battle.time().toString());
                writer.write('\t');
                writer.write(format(battle.first()));
                writer.write('\t');
                writer.write(format(battle.second()));
                writer.write('\n');
            }
        }
        // 書きかけのファイルを読まないよう、書き終えてから差し替える。
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static String format(TopBattle.Side side) {
        return String.join("\t", side.tag(),
                String.join(",", side.cardIds().stream().map(String::valueOf).toList()),
                String.valueOf(side.crowns()), String.valueOf(side.elixirLeaked()));
    }

    private static TopBattle parse(String line) {
        String[] columns = line.split("\t", -1);
        return new TopBattle(Instant.parse(columns[0]), parseSide(columns, 1), parseSide(columns, 5));
    }

    private static TopBattle.Side parseSide(String[] columns, int start) {
        List<Integer> cards = Arrays.stream(columns[start + 1].split(","))
                .filter(value -> !value.isBlank())
                .map(Integer::valueOf)
                .toList();
        return new TopBattle.Side(columns[start], cards, Integer.parseInt(columns[start + 2]),
                Double.parseDouble(columns[start + 3]));
    }
}
