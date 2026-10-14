package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonRankLogTest {

    private static RankingSnapshot snapshot(String at, String season, RankingSnapshot.Entry... entries) {
        return new RankingSnapshot(Instant.parse(at), season, List.of(entries));
    }

    private static RankingSnapshot.Entry entry(int rank, String tag, int rating) {
        return new RankingSnapshot.Entry(rank, tag, tag, rating);
    }

    @Test
    void 同じ時間の記録は前の分を置き換え_その時間に圏外になった人の点も消す() {
        SeasonRankLog log = new SeasonRankLog("2026-09");
        log.add(snapshot("2026-10-07T10:45:00Z", "2026-09", entry(1, "#A", 3000)));
        log.add(snapshot("2026-10-07T11:00:00Z", "2026-09", entry(1, "#A", 3010), entry(2, "#B", 2990)));
        log.add(snapshot("2026-10-07T11:15:00Z", "2026-09", entry(1, "#A", 3020)));

        assertEquals(List.of(
                new SeasonRankLog.Point(Instant.parse("2026-10-07T10:45:00Z"), 1, 3000),
                new SeasonRankLog.Point(Instant.parse("2026-10-07T11:15:00Z"), 1, 3020)), log.of("#A"));
        assertEquals(List.of(), log.of("#B"));
        assertEquals(Instant.parse("2026-10-07T11:15:00Z"), log.lastAt());
    }

    @Test
    void 違うシーズンの記録は足さず_古い記録は無視する() {
        SeasonRankLog log = new SeasonRankLog("2026-09");
        log.add(snapshot("2026-10-07T11:00:00Z", "2026-09", entry(5, "#A", 3000)));

        assertFalse(log.add(snapshot("2026-11-02T10:00:00Z", "2026-10", entry(1, "#A", 2000))));
        assertTrue(log.add(snapshot("2026-10-07T09:00:00Z", "2026-09", entry(1, "#A", 3500))));
        assertEquals(List.of(new SeasonRankLog.Point(Instant.parse("2026-10-07T11:00:00Z"), 5, 3000)), log.of("#A"));
    }

    @Test
    void タグの書き方の違いを吸収する() {
        SeasonRankLog log = new SeasonRankLog("2026-09");
        log.add(snapshot("2026-10-07T11:00:00Z", "2026-09", entry(1, "#ABC", 3000)));

        assertEquals(1, log.of("abc").size());
    }
}
