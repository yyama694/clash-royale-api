package com.example.clashroyaleapi.store;

import com.example.clashroyaleapi.domain.CardForm;
import com.example.clashroyaleapi.domain.DeckWinRates;

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
 * {@link DeckWinRates} の保存先。1行目が「集計日時(ISO形式) \t 数えた対戦の数」(前のシーズンの順位で集めたときは
 * \t の後にそのシーズン "2026-09" など)、2行目以降が1デッキ1行で
 * 「カードID(カンマ区切り) \t 形(カンマ区切り。対戦の evolutionLevel と同じ数) \t タワーユニットID \t 対戦数 \t 勝ち数
 * \t 使った人数 \t 1人の最多の対戦数 \t 最上位の人のタグ \t その人の順位 \t その人の名前」。
 * 名前はプレイヤーが自由に付けるので、タブ・改行を含まないよう空白に置き換える。
 * 集計には1時間近くかかるため、再起動(デプロイ)のたびに前回の結果が消えないようファイルに残す({@link TopDecksFile} と同じ)。
 */
public class DeckWinRatesFile {

    private final Path file;

    public DeckWinRatesFile(Path file) {
        this.file = file;
    }

    /** 未作成、または読めない形式なら空(次の集計で作り直される)。 */
    public Optional<DeckWinRates> read() throws IOException {
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
            int battles = Integer.parseInt(headerColumns[1]);
            YearMonth finishedSeason = headerColumns.length > 2 ? YearMonth.parse(headerColumns[2]) : null;
            List<DeckWinRates.DeckRecord> decks = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    decks.add(parseDeck(line));
                }
            }
            return Optional.of(new DeckWinRates(collectedAt, finishedSeason, battles, decks));
        } catch (DateTimeException | NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return Optional.empty();
        }
    }

    public void write(DeckWinRates winRates) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            writer.write(winRates.collectedAt().toString());
            writer.write('\t');
            writer.write(String.valueOf(winRates.battles()));
            if (winRates.finishedSeason() != null) {
                writer.write('\t');
                writer.write(winRates.finishedSeason().toString());
            }
            writer.write('\n');
            for (DeckWinRates.DeckRecord deck : winRates.decks()) {
                writer.write(String.join("\t", joinInts(deck.cardIds()),
                        joinInts(deck.forms().stream().map(CardForm::battleLevel).toList()),
                        deck.towerTroopId() == null ? "" : deck.towerTroopId().toString(),
                        String.valueOf(deck.battles()), String.valueOf(deck.wins()), String.valueOf(deck.players()),
                        String.valueOf(deck.maxPlayerBattles()), deck.top().tag(), String.valueOf(deck.top().rank()),
                        deck.top().name().replaceAll("[\\t\\r\\n]", " ")));
                writer.write('\n');
            }
        }
        // 書きかけのファイルを読まないよう、書き終えてから差し替える。
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static DeckWinRates.DeckRecord parseDeck(String line) {
        String[] columns = line.split("\t", -1);
        return new DeckWinRates.DeckRecord(parseInts(columns[0]),
                parseInts(columns[1]).stream().map(CardForm::ofBattle).toList(),
                columns[2].isBlank() ? null : Integer.valueOf(columns[2]),
                new DeckWinRates.TopPlayer(columns[7], columns[9], Integer.parseInt(columns[8])),
                Integer.parseInt(columns[3]), Integer.parseInt(columns[4]), Integer.parseInt(columns[5]),
                Integer.parseInt(columns[6]));
    }

    private static String joinInts(List<Integer> values) {
        return String.join(",", values.stream().map(String::valueOf).toList());
    }

    private static List<Integer> parseInts(String column) {
        return Arrays.stream(column.split(","))
                .filter(value -> !value.isBlank())
                .map(Integer::valueOf)
                .toList();
    }
}
