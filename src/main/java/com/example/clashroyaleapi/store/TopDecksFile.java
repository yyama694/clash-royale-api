package com.example.clashroyaleapi.store;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * {@link TopDecks} の保存先。1行目が集計日時(ISO形式)、2行目以降が1人1行で「カードID(カンマ区切り) \t タワーユニットID」。
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
            Instant collectedAt = Instant.parse(header.strip());
            List<TopDecks.SampledDeck> decks = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    decks.add(parseDeck(line));
                }
            }
            return Optional.of(new TopDecks(collectedAt, decks));
        } catch (DateTimeException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    public void write(TopDecks topDecks) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            writer.write(topDecks.collectedAt().toString());
            writer.write('\n');
            for (TopDecks.SampledDeck deck : topDecks.decks()) {
                writer.write(String.join(",", deck.cardIds().stream().map(String::valueOf).toList()));
                writer.write('\t');
                if (deck.towerTroopId() != null) {
                    writer.write(deck.towerTroopId().toString());
                }
                writer.write('\n');
            }
        }
        // 書きかけのファイルを読まないよう、書き終えてから差し替える。
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static TopDecks.SampledDeck parseDeck(String line) {
        String[] columns = line.split("\t", -1);
        List<Integer> cards = Arrays.stream(columns[0].split(","))
                .filter(id -> !id.isBlank())
                .map(Integer::valueOf)
                .toList();
        Integer tower = columns.length > 1 && !columns[1].isBlank() ? Integer.valueOf(columns[1]) : null;
        return new TopDecks.SampledDeck(cards, tower);
    }
}
