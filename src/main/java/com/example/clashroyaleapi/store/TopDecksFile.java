package com.example.clashroyaleapi.store;

import com.example.clashroyaleapi.domain.CardForm;
import com.example.clashroyaleapi.domain.TopDecks;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * {@link TopDecks} の保存先。1行目が集計日時(ISO形式。前のシーズンの順位で集めたときは \t の後にそのシーズン "2026-09" など)、
 * 2行目以降が1人1行で
 * 「カードID(カンマ区切り) \t タワーユニットID \t タグ \t 順位 \t レーティング \t レベル(カンマ区切り) \t タワーユニットのレベル \t 名前
 * \t 形(カンマ区切り。対戦の evolutionLevel と同じ数で、0が通常)」。
 * 3〜8列目は2026-09-27に、9列目は2026-10-03に足した。それより前のファイル(2列・8列)も読め、そのときは player が null、
 * または形が空になる。
 * 名前はプレイヤーが自由に付けるので、タブ・改行を含まないよう空白に置き換える。
 * 集計には1時間近くかかるため、再起動(デプロイ)のたびに前回の結果が消えないようファイルに残す。
 */
public class TopDecksFile {

    private final Path file;

    public TopDecksFile(Path file) {
        this.file = file;
    }

    /** 未作成、または読めない形式なら空(次の集計で作り直される)。 */
    public Optional<TopDecks> read() throws IOException {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) {
                return Optional.empty();
            }
            String[] headerColumns = header.strip().split("\t");
            Instant collectedAt = Instant.parse(headerColumns[0]);
            YearMonth finishedSeason = headerColumns.length > 1 ? YearMonth.parse(headerColumns[1]) : null;
            List<TopDecks.SampledDeck> decks = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    decks.add(parseDeck(line));
                }
            }
            return Optional.of(new TopDecks(collectedAt, decks, finishedSeason));
        } catch (DateTimeException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    public void write(TopDecks topDecks) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            writer.write(topDecks.collectedAt().toString());
            if (topDecks.finishedSeason() != null) {
                writer.write('\t');
                writer.write(topDecks.finishedSeason().toString());
            }
            writer.write('\n');
            for (TopDecks.SampledDeck deck : topDecks.decks()) {
                List<String> columns = new ArrayList<>(List.of(joinInts(deck.cardIds()), orEmpty(deck.towerTroopId())));
                TopDecks.Player player = deck.player();
                if (player != null) {
                    columns.addAll(List.of(player.tag(), String.valueOf(player.rank()), String.valueOf(player.rating()),
                            joinInts(player.levels()), orEmpty(player.towerLevel()),
                            player.name().replaceAll("[\\t\\r\\n]", " "),
                            joinInts(player.forms().stream().map(CardForm::battleLevel).toList())));
                }
                writer.write(String.join("\t", columns));
                writer.write('\n');
            }
        }
        // 書きかけのファイルを読まないよう、書き終えてから差し替える。
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static TopDecks.SampledDeck parseDeck(String line) {
        String[] columns = line.split("\t", -1);
        List<Integer> cards = parseInts(columns[0]);
        Integer tower = columns.length > 1 ? parseNullable(columns[1]) : null;
        if (columns.length < 8) {
            return new TopDecks.SampledDeck(cards, tower);
        }
        List<CardForm> forms = columns.length < 9 ? List.of()
                : parseInts(columns[8]).stream().map(CardForm::ofBattle).toList();
        return new TopDecks.SampledDeck(cards, tower, new TopDecks.Player(columns[2], columns[7],
                Integer.parseInt(columns[3]), Integer.parseInt(columns[4]), parseInts(columns[5]),
                parseNullable(columns[6]), forms));
    }

    private static String joinInts(List<Integer> values) {
        return String.join(",", values.stream().map(String::valueOf).toList());
    }

    private static String orEmpty(Integer value) {
        return value == null ? "" : value.toString();
    }

    private static List<Integer> parseInts(String column) {
        return Arrays.stream(column.split(","))
                .filter(value -> !value.isBlank())
                .map(Integer::valueOf)
                .toList();
    }

    private static Integer parseNullable(String column) {
        return column.isBlank() ? null : Integer.valueOf(column);
    }
}
