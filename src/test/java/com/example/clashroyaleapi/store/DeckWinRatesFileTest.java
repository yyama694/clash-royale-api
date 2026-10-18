package com.example.clashroyaleapi.store;

import com.example.clashroyaleapi.domain.CardForm;
import com.example.clashroyaleapi.domain.DeckWinRates;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckWinRatesFileTest {

    @TempDir
    Path dir;

    @Test
    void 書いた内容をそのまま読み戻せる() throws IOException {
        DeckWinRatesFile file = new DeckWinRatesFile(dir.resolve("card-usage").resolve("deck-win-rates.tsv"));
        DeckWinRates written = new DeckWinRates(Instant.parse("2026-10-18T09:00:00Z"), null, 11750, List.of(
                new DeckWinRates.DeckRecord(List.of(26000000, 26000001), List.of(CardForm.EVOLUTION, CardForm.NORMAL),
                        159000000, new DeckWinRates.TopPlayer("#ABC", "名前 スペース", 3), 881, 510, 113, 44),
                new DeckWinRates.DeckRecord(List.of(26000002), List.of(CardForm.HERO), null,
                        new DeckWinRates.TopPlayer("#DEF", "Miku", 12), 50, 25, 10, 15)));

        file.write(written);

        assertEquals(Optional.of(written), file.read());
    }

    @Test
    void 前のシーズンの順位で集めたことも読み戻せ名前のタブは空白にする() throws IOException {
        DeckWinRatesFile file = new DeckWinRatesFile(dir.resolve("deck-win-rates.tsv"));
        DeckWinRates written = new DeckWinRates(Instant.parse("2026-10-06T00:00:00Z"), YearMonth.of(2026, 9), 1,
                List.of(new DeckWinRates.DeckRecord(List.of(26000000), List.of(CardForm.NORMAL), null,
                        new DeckWinRates.TopPlayer("#ABC", "a\tb", 1), 1, 1, 1, 1)));

        file.write(written);

        assertEquals("a b", file.read().orElseThrow().decks().get(0).top().name());
        assertEquals(YearMonth.of(2026, 9), file.read().orElseThrow().finishedSeason());
        assertTrue(Files.readString(dir.resolve("deck-win-rates.tsv")).startsWith("2026-10-06T00:00:00Z\t1\t2026-09\n"));
    }

    @Test
    void 無いか読めない形式なら空() throws IOException {
        Path path = dir.resolve("deck-win-rates.tsv");
        assertEquals(Optional.empty(), new DeckWinRatesFile(path).read());

        Files.writeString(path, "broken\n");
        assertEquals(Optional.empty(), new DeckWinRatesFile(path).read());
    }
}
