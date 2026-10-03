package com.example.clashroyaleapi.store;

import com.example.clashroyaleapi.domain.CardForm;
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
    void 誰のデッキかとレベルも読み戻せる() throws IOException {
        TopDecksFile file = new TopDecksFile(dir.resolve("top-decks.tsv"));
        TopDecks written = new TopDecks(Instant.parse("2026-09-27T03:43:58Z"), List.of(
                new TopDecks.SampledDeck(List.of(26000000, 26000001), 159000000,
                        new TopDecks.Player("#ABC", "Miku", 1, 2887, List.of(16, 15), 16)),
                new TopDecks.SampledDeck(List.of(26000002), null,
                        new TopDecks.Player("#DEF", "名前 スペース", 2, 2882, List.of(14), null))));

        file.write(written);

        assertEquals(Optional.of(written), file.read());
    }

    @Test
    void 対戦で使った形も読み戻せる() throws IOException {
        TopDecksFile file = new TopDecksFile(dir.resolve("top-decks.tsv"));
        TopDecks written = new TopDecks(Instant.parse("2026-10-03T03:43:58Z"), List.of(
                new TopDecks.SampledDeck(List.of(26000000, 26000001, 26000002), 159000000,
                        new TopDecks.Player("#ABC", "Miku", 1, 2887, List.of(16, 16, 16), 16,
                                List.of(CardForm.EVOLUTION, CardForm.HERO, CardForm.NORMAL)))));

        file.write(written);

        assertEquals(Optional.of(written), file.read());
    }

    @Test
    void 形を持たない形式は形が空として読む() throws IOException {
        Path path = dir.resolve("before-forms.tsv");
        Files.writeString(path, "2026-09-27T03:43:58Z\n26000000,26000001\t159000000\t#ABC\t1\t2887\t16,15\t16\tMiku\n");

        TopDecks.Player player = new TopDecksFile(path).read().orElseThrow().decks().get(0).player();

        assertEquals(List.of(), player.forms());
        assertEquals(CardForm.NORMAL, player.formAt(0));
    }

    @Test
    void 名前のタブと改行は空白にして列を崩さない() throws IOException {
        TopDecksFile file = new TopDecksFile(dir.resolve("top-decks.tsv"));
        file.write(new TopDecks(Instant.parse("2026-09-27T03:43:58Z"), List.of(new TopDecks.SampledDeck(
                List.of(1), null, new TopDecks.Player("#ABC", "a\tb\nc", 1, 2887, List.of(16), null)))));

        assertEquals("a b c", file.read().orElseThrow().decks().get(0).player().name());
    }

    @Test
    void 誰のデッキかを持たない古い形式も読める() throws IOException {
        Path path = dir.resolve("legacy.tsv");
        Files.writeString(path, "2026-09-27T03:43:58Z\n26000000,26000001\t159000000\n26000002\t\n");

        List<TopDecks.SampledDeck> decks = new TopDecksFile(path).read().orElseThrow().decks();

        assertEquals(new TopDecks.SampledDeck(List.of(26000000, 26000001), 159000000), decks.get(0));
        assertEquals(new TopDecks.SampledDeck(List.of(26000002), null), decks.get(1));
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
