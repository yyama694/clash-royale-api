package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
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

    /** 1000人そろった記録。top の後ろを、1000位のレーティングが floor になるよう埋める。 */
    private static RankingSnapshot full(String at, int floor, RankingSnapshot.Entry... top) {
        List<RankingSnapshot.Entry> entries = new ArrayList<>(List.of(top));
        for (int rank = entries.size() + 1; rank <= 1000; rank++) {
            entries.add(entry(rank, "#F" + rank, floor + 1000 - rank));
        }
        return new RankingSnapshot(Instant.parse(at), "2026-09", entries);
    }

    private static List<Integer> ranks(SeasonRankLog log, String tag) {
        return log.of(tag).stream().map(SeasonRankLog.Point::rank).toList();
    }

    @Test
    void 同じ時間の記録は前の分を置き換え_その時間に圏外になった人の点も消す() {
        SeasonRankLog log = new SeasonRankLog("2026-09");
        log.add(snapshot("2026-10-07T10:45:00Z", "2026-09", entry(1, "#A", 3000)));
        log.add(snapshot("2026-10-07T11:00:00Z", "2026-09", entry(1, "#A", 3010), entry(2, "#B", 2990)));
        log.add(snapshot("2026-10-07T11:15:00Z", "2026-09", entry(1, "#A", 3020)));

        assertEquals(List.of(
                new SeasonRankLog.Point(Instant.parse("2026-10-07T10:45:00Z"), 1, 3000, 1),
                new SeasonRankLog.Point(Instant.parse("2026-10-07T11:15:00Z"), 1, 3020, 1)), log.of("#A"));
        assertEquals(List.of(), log.of("#B"));
        assertEquals(Instant.parse("2026-10-07T11:15:00Z"), log.lastAt());
    }

    @Test
    void 違うシーズンの記録は足さず_古い記録は無視する() {
        SeasonRankLog log = new SeasonRankLog("2026-09");
        log.add(snapshot("2026-10-07T11:00:00Z", "2026-09", entry(5, "#A", 3000)));

        assertFalse(log.add(snapshot("2026-11-02T10:00:00Z", "2026-10", entry(1, "#A", 2000))));
        assertTrue(log.add(snapshot("2026-10-07T09:00:00Z", "2026-09", entry(1, "#A", 3500))));
        assertEquals(List.of(new SeasonRankLog.Point(Instant.parse("2026-10-07T11:00:00Z"), 5, 3000, 1)),
                log.of("#A"));
    }

    @Test
    void タグの書き方の違いを吸収する() {
        SeasonRankLog log = new SeasonRankLog("2026-09");
        log.add(snapshot("2026-10-07T11:00:00Z", "2026-09", entry(1, "#ABC", 3000)));

        assertEquals(1, log.of("abc").size());
    }

    @Test
    void 各点にその時点のランキングの人数を持ち_1000人に満たなければそろっていないとみなす() {
        SeasonRankLog log = new SeasonRankLog("2026-09");
        log.add(snapshot("2026-10-07T12:00:00Z", "2026-09", entry(1, "#B", 1800), entry(2, "#A", 1700)));
        log.add(full("2026-10-09T09:00:00Z", 1200, entry(1, "#A", 1900)));

        List<SeasonRankLog.Point> points = log.of("#A");
        assertEquals(List.of(2, 1000), points.stream().map(SeasonRankLog.Point::ranked).toList());
        assertTrue(points.get(0).rankingIncomplete());
        assertFalse(points.get(1).rankingIncomplete());
    }

    @Test
    void 公式のランキングが作り直されて崩れた記録は_1000位のレーティングが元に戻るまで足さない() {
        // 2026-10-15の実例: 1000位(2,028)より下の人が、上位が消えた間だけ381位に繰り上がった
        SeasonRankLog log = new SeasonRankLog("2026-09");
        log.add(full("2026-10-15T07:01:00Z", 2028, entry(1, "#TOP", 2663), entry(2, "#A", 2100)));
        log.add(snapshot("2026-10-15T07:16:00Z", "2026-09", entry(1, "#TOP", 2663), entry(381, "#ME", 2017)));
        log.add(full("2026-10-15T07:31:00Z", 1784, entry(1, "#TOP", 2663), entry(471, "#ME", 2017)));
        log.add(full("2026-10-15T16:01:00Z", 2025, entry(1, "#TOP", 2682), entry(999, "#ME", 2017)));
        log.add(full("2026-10-15T16:46:00Z", 2029, entry(1, "#TOP", 2682)));

        assertEquals(List.of(), log.of("#ME"));
        assertEquals(List.of(Instant.parse("2026-10-15T07:01:00Z"), Instant.parse("2026-10-15T16:46:00Z")),
                log.of("#TOP").stream().map(SeasonRankLog.Point::at).toList());
        assertEquals(Instant.parse("2026-10-15T16:46:00Z"), log.lastAt());
    }

    @Test
    void 崩れた記録を挟んでも_同じ時間の点は最後に足した記録で置き換える() {
        SeasonRankLog log = new SeasonRankLog("2026-09");
        log.add(full("2026-10-15T07:01:00Z", 2028, entry(10, "#A", 2300), entry(20, "#B", 2200)));
        log.add(full("2026-10-15T07:16:00Z", 1900, entry(5, "#A", 2300)));
        log.add(full("2026-10-15T07:31:00Z", 2029, entry(11, "#A", 2300)));

        assertEquals(List.of(11), ranks(log, "#A"));
        assertEquals(List.of(), log.of("#B"));
    }

    @Test
    void ふだんの小さな上下では捨てない() {
        SeasonRankLog log = new SeasonRankLog("2026-09");
        log.add(full("2026-10-20T11:46:00Z", 2300, entry(10, "#A", 2400)));
        log.add(full("2026-10-20T12:01:00Z", 2298, entry(11, "#A", 2400)));
        log.add(full("2026-10-20T13:01:00Z", 2280, entry(12, "#A", 2400)));

        assertEquals(List.of(10, 11, 12), ranks(log, "#A"));
    }

    @Test
    void 作り直しの前の水準に24時間戻らなければ_また足し始める() {
        SeasonRankLog log = new SeasonRankLog("2026-09");
        log.add(full("2026-10-15T07:01:00Z", 2028, entry(10, "#A", 2300)));
        log.add(full("2026-10-15T08:01:00Z", 1500, entry(5, "#A", 2300)));
        log.add(full("2026-10-16T07:46:00Z", 1600, entry(6, "#A", 2300)));
        log.add(full("2026-10-16T08:01:00Z", 1600, entry(7, "#A", 2300)));
        log.add(full("2026-10-16T09:01:00Z", 1601, entry(8, "#A", 2300)));

        assertEquals(List.of(10, 7, 8), ranks(log, "#A"));
    }
}
