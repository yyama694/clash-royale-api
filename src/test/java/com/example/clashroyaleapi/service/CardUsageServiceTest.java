package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.domain.TopDecks;
import com.example.clashroyaleapi.store.TopDecksFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardUsageServiceTest {

    @TempDir
    Path dir;

    @Test
    void 集計のたびに日付と時刻のファイルへ履歴を残す() throws IOException {
        CardUsageService service = new CardUsageService(new TopDecksFile(dir.resolve("top-decks.tsv")),
                dir.resolve("history"));
        TopDecks first = topDecks("2026-10-10T11:20:27Z", null);
        TopDecks second = topDecks("2026-10-11T11:25:00Z", YearMonth.of(2026, 9));

        service.publish(first);
        service.publish(second);

        assertEquals(Optional.of(first),
                new TopDecksFile(dir.resolve("history/2026-10-10/112027.tsv")).read());
        assertEquals(Optional.of(second),
                new TopDecksFile(dir.resolve("history/2026-10-11/112500.tsv")).read());
        assertEquals(Optional.of(second), new TopDecksFile(dir.resolve("top-decks.tsv")).read());
    }

    @Test
    void その時刻より前の最後の集計を履歴から読み_無ければ空() throws IOException {
        CardUsageService service = new CardUsageService(new TopDecksFile(dir.resolve("top-decks.tsv")),
                dir.resolve("history"));
        assertEquals(Optional.empty(), service.lastBefore(Instant.parse("2026-10-12T00:00:00Z")));

        service.publish(topDecks("2026-10-10T11:20:27Z", null));
        service.publish(topDecks("2026-10-11T11:25:00Z", null));
        service.publish(topDecks("2026-10-12T11:30:00Z", null));

        CardUsageService restarted = new CardUsageService(new TopDecksFile(dir.resolve("top-decks.tsv")),
                dir.resolve("history"));
        assertEquals(Instant.parse("2026-10-11T11:25:00Z"),
                restarted.lastBefore(Instant.parse("2026-10-12T00:00:00Z")).orElseThrow().collectedAt());
        assertEquals(Optional.empty(), restarted.lastBefore(Instant.parse("2026-10-10T11:20:27Z")));
    }

    private static TopDecks topDecks(String collectedAt, YearMonth finishedSeason) {
        return new TopDecks(Instant.parse(collectedAt), List.of(
                new TopDecks.SampledDeck(List.of(26000000, 26000001), 159000000,
                        new TopDecks.Player("#ABC", "Miku", 1, 2887, List.of(16, 16), 16))),
                finishedSeason);
    }
}
