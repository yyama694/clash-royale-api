package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaderHistoryTest {

    private static final RankingSnapshot.Entry MIKU = new RankingSnapshot.Entry(1, "#MIKU", "Miku", 3000);
    private static final RankingSnapshot.Entry TARO = new RankingSnapshot.Entry(1, "#TARO", "Taro", 3010);

    private static LeaderHistory.Point point(String at, String season, RankingSnapshot.Entry leader) {
        return new LeaderHistory.Point(Instant.parse(at), season, leader);
    }

    @Test
    void 首位交代を新しい順に返す() {
        LeaderHistory history = new LeaderHistory(List.of(
                point("2026-10-06T00:00:00Z", "2026-09", MIKU),
                point("2026-10-06T00:15:00Z", "2026-09", TARO),
                point("2026-10-06T00:30:00Z", "2026-09", TARO),
                point("2026-10-06T00:45:00Z", "2026-09", MIKU)));

        List<LeaderHistory.Change> changes = history.changes(Instant.parse("2026-10-06T00:00:00Z"),
                Instant.parse("2026-10-06T00:45:00Z"));

        assertEquals(List.of(
                new LeaderHistory.Change(Instant.parse("2026-10-06T00:45:00Z"), MIKU, TARO),
                new LeaderHistory.Change(Instant.parse("2026-10-06T00:15:00Z"), TARO, MIKU)), changes);
    }

    @Test
    void 期間の外の首位交代は数えない() {
        LeaderHistory history = new LeaderHistory(List.of(
                point("2026-10-05T23:45:00Z", "2026-09", MIKU),
                point("2026-10-06T00:00:00Z", "2026-09", TARO),
                point("2026-10-06T00:15:00Z", "2026-09", MIKU)));

        assertEquals(1, history.changes(Instant.parse("2026-10-06T00:15:00Z"),
                Instant.parse("2026-10-06T00:15:00Z")).size());
    }

    @Test
    void シーズンが替わった最初の1位は首位交代と数えない() {
        LeaderHistory history = new LeaderHistory(List.of(
                point("2026-10-05T08:45:00Z", "2026-08", MIKU),
                point("2026-10-05T12:00:00Z", "2026-09", TARO)));

        assertEquals(List.of(), history.changes(Instant.MIN, Instant.MAX));
    }

    @Test
    void 今の1位がいつから続けて1位かを返す() {
        LeaderHistory history = new LeaderHistory(List.of(
                point("2026-10-06T00:00:00Z", "2026-09", MIKU),
                point("2026-10-06T00:15:00Z", "2026-09", TARO),
                point("2026-10-06T00:30:00Z", "2026-09", TARO)));

        LeaderHistory.Reign reign = history.reignAt(Instant.parse("2026-10-06T00:30:00Z")).orElseThrow();

        assertEquals(TARO, reign.leader());
        assertEquals(Instant.parse("2026-10-06T00:15:00Z"), reign.since());
        assertFalse(reign.sinceFirstRecord());
    }

    @Test
    void シーズンの最初の記録から1位のままなら本当の始まりは分からないとする() {
        LeaderHistory history = new LeaderHistory(List.of(
                point("2026-10-05T08:45:00Z", "2026-08", TARO),
                point("2026-10-05T12:00:00Z", "2026-09", TARO),
                point("2026-10-05T12:15:00Z", "2026-09", TARO)));

        LeaderHistory.Reign reign = history.reignAt(Instant.parse("2026-10-05T12:15:00Z")).orElseThrow();

        assertEquals(Instant.parse("2026-10-05T12:00:00Z"), reign.since());
        assertTrue(reign.sinceFirstRecord());
    }

    @Test
    void 指定した時刻より後の記録は見ない() {
        LeaderHistory history = new LeaderHistory(List.of(
                point("2026-10-06T00:00:00Z", "2026-09", MIKU),
                point("2026-10-06T00:15:00Z", "2026-09", TARO)));

        assertEquals(MIKU, history.reignAt(Instant.parse("2026-10-06T00:10:00Z")).orElseThrow().leader());
        assertEquals(Optional.empty(), history.reignAt(Instant.parse("2026-10-05T00:00:00Z")));
    }
}
