package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.domain.RankingSnapshot;
import com.example.clashroyaleapi.store.RankingSnapshotStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankingHistoryServiceTest {

    @TempDir
    Path dir;

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneOffset.UTC);

    private static RankingSnapshot snapshot(String at, String season, String leader, int rating) {
        return new RankingSnapshot(Instant.parse(at), season, List.of(
                new RankingSnapshot.Entry(1, "#" + leader, leader, rating),
                new RankingSnapshot.Entry(2, "#Z", "Z", 2000)));
    }

    private RankingHistoryService serviceWith(RankingSnapshot... snapshots) throws IOException {
        RankingSnapshotStore store = new RankingSnapshotStore(dir);
        for (RankingSnapshot snapshot : snapshots) {
            store.write(snapshot);
        }
        return new RankingHistoryService(store, NOW);
    }

    @Test
    void まだ記録が無ければ最新の動きは空() throws IOException {
        assertEquals(Optional.empty(), serviceWith().latest());
    }

    @Test
    void 最新の記録を24時間前以降で最も古い同じシーズンの記録と比べる() throws IOException {
        RankingHistoryService service = serviceWith(
                snapshot("2026-10-06T11:00:00Z", "2026-09", "A", 2900),
                snapshot("2026-10-06T12:30:00Z", "2026-09", "A", 2910),
                snapshot("2026-10-06T18:00:00Z", "2026-09", "B", 2950),
                snapshot("2026-10-07T11:45:00Z", "2026-09", "B", 2990));

        RankingHistoryService.Report report = service.latest().orElseThrow();

        assertEquals(Instant.parse("2026-10-07T11:45:00Z"), report.movements().after().takenAt());
        assertEquals(Instant.parse("2026-10-06T12:30:00Z"), report.movements().before().takenAt());
        assertEquals(1, report.leaderChanges().size());
        assertEquals(Instant.parse("2026-10-06T18:00:00Z"), report.reign().since());
        assertNull(report.day());
    }

    @Test
    void 前のシーズンの記録とは比べない() throws IOException {
        RankingHistoryService service = serviceWith(
                snapshot("2026-10-05T08:45:00Z", "2026-08", "A", 3500),
                snapshot("2026-10-06T09:00:00Z", "2026-09", "B", 2100));

        RankingHistoryService.Report report = service.latest().orElseThrow();

        assertNull(report.movements().before());
        assertEquals(List.of(), report.leaderChanges());
    }

    @Test
    void 新しい記録を足すと最新の動きに反映される() throws IOException {
        RankingHistoryService service = serviceWith(snapshot("2026-10-07T11:00:00Z", "2026-09", "A", 2900));

        service.record(snapshot("2026-10-07T11:15:00Z", "2026-09", "B", 2950));

        RankingHistoryService.Report report = service.latest().orElseThrow();
        assertEquals("B", report.movements().after().leader().orElseThrow().name());
        assertEquals(Instant.parse("2026-10-07T11:00:00Z"), report.movements().before().takenAt());
    }

    @Test
    void 最新の記録が1時間より古ければ記録が止まっているとみなす() throws IOException {
        RankingHistoryService stopped = serviceWith(snapshot("2026-10-07T10:46:00Z", "2026-09", "A", 2900));
        assertTrue(stopped.isStale(stopped.latest().orElseThrow()));

        RankingHistoryService running = serviceWith(snapshot("2026-10-07T11:46:00Z", "2026-09", "A", 2900));
        assertFalse(running.isStale(running.latest().orElseThrow()));
    }

    @Test
    void 日ごとの動きは記録が止まっているとはみなさない() throws IOException {
        RankingHistoryService service = serviceWith(snapshot("2026-10-05T10:00:00Z", "2026-09", "A", 2900));

        assertFalse(service.isStale(service.day(LocalDate.parse("2026-10-05")).orElseThrow()));
    }

    @Test
    void その日の動きは前の日の最後の記録とその日の最後の記録を比べる() throws IOException {
        RankingHistoryService service = serviceWith(
                snapshot("2026-10-05T23:46:00Z", "2026-09", "A", 2900),
                snapshot("2026-10-06T00:01:00Z", "2026-09", "B", 2905),
                snapshot("2026-10-06T23:46:00Z", "2026-09", "B", 2950),
                snapshot("2026-10-07T00:01:00Z", "2026-09", "C", 2960));

        RankingHistoryService.Report report = service.day(LocalDate.parse("2026-10-06")).orElseThrow();

        assertEquals(Instant.parse("2026-10-05T23:46:00Z"), report.movements().before().takenAt());
        assertEquals(Instant.parse("2026-10-06T23:46:00Z"), report.movements().after().takenAt());
        assertEquals(1, report.leaderChanges().size());
        assertEquals("B", report.leaderChanges().getFirst().leader().name());
        assertEquals(LocalDate.parse("2026-10-06"), report.day());
    }

    @Test
    void 前の日の記録が無ければその日の最初の記録と比べる() throws IOException {
        RankingHistoryService service = serviceWith(
                snapshot("2026-10-06T09:01:00Z", "2026-09", "A", 2100),
                snapshot("2026-10-06T23:46:00Z", "2026-09", "A", 2400));

        RankingHistoryService.Report report = service.day(LocalDate.parse("2026-10-06")).orElseThrow();

        assertEquals(Instant.parse("2026-10-06T09:01:00Z"), report.movements().before().takenAt());
    }

    @Test
    void 終わっていない日と記録の無い日は空() throws IOException {
        RankingHistoryService service = serviceWith(snapshot("2026-10-07T00:01:00Z", "2026-09", "A", 2900));

        assertEquals(Optional.empty(), service.day(LocalDate.parse("2026-10-07")));
        assertEquals(Optional.empty(), service.day(LocalDate.parse("2026-10-04")));
    }

    @Test
    void 記録のある終わった日を新しい順に返す() throws IOException {
        RankingHistoryService service = serviceWith(
                snapshot("2026-10-05T10:00:00Z", "2026-09", "A", 2900),
                snapshot("2026-10-06T10:00:00Z", "2026-09", "A", 2900),
                snapshot("2026-10-06T11:00:00Z", "2026-09", "A", 2900),
                snapshot("2026-10-07T10:00:00Z", "2026-09", "A", 2900));

        assertEquals(List.of(LocalDate.parse("2026-10-06"), LocalDate.parse("2026-10-05")), service.archiveDays());
        assertFalse(service.archiveDays().contains(LocalDate.parse("2026-10-07")));
    }
}
