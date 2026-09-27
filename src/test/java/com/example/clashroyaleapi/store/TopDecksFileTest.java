package com.example.clashroyaleapi.store;

import com.example.clashroyaleapi.domain.TopDecks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopDecksFileTest {

    @TempDir
    Path dir;

    @Test
    void 書いた内容をそのまま読み戻せる() throws IOException {
        TopDecksFile file = new TopDecksFile(dir.resolve("card-usage").resolve("top-decks.tsv"));
        TopDecks written = new TopDecks(Instant.parse("2026-09-26T01:02:03Z"), List.of(
                new TopDecks.SampledDeck(List.of(26000000, 26000001), 159000000),
                new TopDecks.SampledDeck(List.of(26000002), null)));

        file.write(written);

        assertEquals(Optional.of(written), file.read());
    }

    @Test
    void ファイルが無ければ空() throws IOException {
        assertTrue(new TopDecksFile(dir.resolve("missing.tsv")).read().isEmpty());
    }

    @Test
    void 読めない形式なら空にして作り直させる() throws IOException {
        Path path = dir.resolve("broken.tsv");
        Files.writeString(path, "not a date\n1,2\t\n");

        assertTrue(new TopDecksFile(path).read().isEmpty());
    }
}
