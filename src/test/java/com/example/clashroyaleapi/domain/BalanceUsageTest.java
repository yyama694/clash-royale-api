package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalanceUsageTest {

    private static final BalanceUpdate UPDATE = BalanceUpdate.parse(List.of(
            "update\t2026-10-19\thttps://example.com",
            "change\tMinion Giant\tnormal\tnerf\tdamage\t168\t153\t-9",
            "change\tRune Giant\tnormal\tnerf\tdamage\t1\t2\t3",
            "change\tSkeleton Barrel\tevolution\trework\tdeathDamage\t192\t163\t-15",
            "change\tVoid\tnormal\tbuff\tdamage\t296\t307\t3")).getFirst();

    private static BalanceSnapshot snapshot(String time, Map<String, Double> percents) {
        Instant at = Instant.parse(time);
        return new BalanceSnapshot(LocalDate.ofInstant(at, ZoneOffset.UTC), at, 1000,
                (card, form) -> Optional.ofNullable(percents.get(card + "/" + form)));
    }

    @Test
    void 調整前から日数ごとの集計と今までを並べ_今と同じ集計の日は今だけにする() {
        BalanceSnapshot before = snapshot("2026-10-18T12:00:00Z", Map.of("Minion Giant/NORMAL", 12.6));
        BalanceSnapshot day1 = snapshot("2026-10-20T07:00:00Z", Map.of("Minion Giant/NORMAL", 7.6));
        BalanceSnapshot day3 = snapshot("2026-10-22T07:00:00Z", Map.of("Minion Giant/NORMAL", 7.0));

        BalanceUsage usage = BalanceUsage.of(UPDATE, before, Map.of(1, day1, 3, day3), day3, Map.of());

        BalanceUsage.Series series = usage.of("Minion Giant", CardForm.NORMAL);
        assertEquals(12.6, series.before());
        assertEquals(List.of(new BalanceUsage.Point(BalanceUsage.PointKind.DAYS_AFTER, 1, 7.6),
                new BalanceUsage.Point(BalanceUsage.PointKind.NOW, 0, 7.0)), series.after());
        assertEquals(-5.6, series.delta());
    }

    @Test
    void 後の調整でまた変わったカードは次の調整の前の集計で止め_その後の日数の集計は出さない() {
        BalanceSnapshot before = snapshot("2026-10-18T12:00:00Z", Map.of("Minion Giant/NORMAL", 12.6));
        BalanceSnapshot day1 = snapshot("2026-10-20T07:00:00Z", Map.of("Minion Giant/NORMAL", 7.6));
        BalanceSnapshot day7 = snapshot("2026-10-26T07:00:00Z", Map.of("Minion Giant/NORMAL", 3.0));
        BalanceSnapshot lastBeforeNext = snapshot("2026-10-21T07:00:00Z", Map.of("Minion Giant/NORMAL", 7.2));
        BalanceSnapshot latest = snapshot("2026-10-30T07:00:00Z", Map.of("Minion Giant/NORMAL", 1.0));
        Map<String, BalanceUsage.Next> nexts = Map.of("Minion Giant",
                new BalanceUsage.Next(Instant.parse("2026-10-22T00:00:00Z"), lastBeforeNext));

        BalanceUsage usage = BalanceUsage.of(UPDATE, before, Map.of(1, day1, 7, day7), latest, nexts);

        assertEquals(List.of(new BalanceUsage.Point(BalanceUsage.PointKind.DAYS_AFTER, 1, 7.6),
                        new BalanceUsage.Point(BalanceUsage.PointKind.BEFORE_NEXT, 0, 7.2)),
                usage.of("Minion Giant", CardForm.NORMAL).after());
    }

    @Test
    void 調整前の記録が無ければ差は出さず_調整後の集計が無ければ何も出さない() {
        BalanceSnapshot latest = snapshot("2026-10-20T07:00:00Z", Map.of("Minion Giant/NORMAL", 7.6));

        BalanceUsage usage = BalanceUsage.of(UPDATE, null, Map.of(), latest, Map.of());
        assertNull(usage.of("Minion Giant", CardForm.NORMAL).before());
        assertNull(usage.of("Minion Giant", CardForm.NORMAL).delta());

        // 調整の前の集計しか無い(調整の当日に見た)ときは、今の値を出さない。
        BalanceUsage sameDay = BalanceUsage.of(UPDATE, null, Map.of(),
                snapshot("2026-10-18T12:00:00Z", Map.of("Minion Giant/NORMAL", 12.6)), Map.of());
        assertTrue(sameDay.of("Minion Giant", CardForm.NORMAL).after().isEmpty());
    }

    @Test
    void 種類ごとのまとめは平均と変化がいちばん大きいカード_同じなら記事の順で先() {
        BalanceSnapshot before = snapshot("2026-10-18T12:00:00Z", Map.of(
                "Minion Giant/NORMAL", 12.6, "Rune Giant/NORMAL", 4.9, "Void/NORMAL", 1.0,
                "Skeleton Barrel/EVOLUTION", 2.0));
        BalanceSnapshot latest = snapshot("2026-10-20T07:00:00Z", Map.of(
                "Minion Giant/NORMAL", 7.6, "Rune Giant/NORMAL", -0.1, "Void/NORMAL", 1.1,
                "Skeleton Barrel/EVOLUTION", 2.0));

        BalanceUsage usage = BalanceUsage.of(UPDATE, before, Map.of(), latest, Map.of());

        assertEquals(Optional.of(new BalanceUsage.Digest(2, -5.0,
                        new BalanceUsage.Key("Minion Giant", CardForm.NORMAL), -5.0)),
                usage.digest(UPDATE, BalanceUpdate.Kind.NERF));
        assertEquals(Optional.of(new BalanceUsage.Digest(1, 0.1, new BalanceUsage.Key("Void", CardForm.NORMAL), 0.1)),
                usage.digest(UPDATE, BalanceUpdate.Kind.BUFF));
        assertEquals(0.0, usage.digest(UPDATE, BalanceUpdate.Kind.REWORK).orElseThrow().largestDelta());
        assertTrue(usage.digest(UPDATE, BalanceUpdate.Kind.FIX).isEmpty());
    }
}
