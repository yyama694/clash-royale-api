package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BalanceBaselineTest {

    @Test
    void 手で残した使用率を調整の実施日ごとに読み_書いていないカードは空() {
        Map<LocalDate, BalanceBaseline> baselines = BalanceBaseline.parse(List.of(
                "# コメント",
                "2026-10-06\t2026-09-30\t981\tBarbarian Barrel\t38.9",
                "2026-10-06\t2026-09-30\t981\tSkeletons\t35.9"));

        BalanceBaseline october = baselines.get(LocalDate.of(2026, 10, 6));
        assertEquals(LocalDate.of(2026, 9, 30), october.collectedOn());
        assertEquals(981, october.sampleSize());
        assertEquals(Optional.of(38.9), october.percent().apply("Barbarian Barrel"));
        assertEquals(Optional.empty(), october.percent().apply("Royal Ghost"));
    }

    @Test
    void 集計の履歴からは_集計に無いカードを0パーセントにし_一覧に無いカードは空() {
        TopDecks decks = new TopDecks(Instant.parse("2026-11-01T11:00:00Z"), List.of(
                new TopDecks.SampledDeck(List.of(1, 2), null),
                new TopDecks.SampledDeck(List.of(1, 3), null)));

        BalanceBaseline baseline = BalanceBaseline.of(CardUsage.of(decks), Map.of("Knight", 1, "Archers", 9));

        assertEquals(LocalDate.of(2026, 11, 1), baseline.collectedOn());
        assertEquals(2, baseline.sampleSize());
        assertEquals(Optional.of(100.0), baseline.percent().apply("Knight"));
        assertEquals(Optional.of(0.0), baseline.percent().apply("Archers"));
        assertEquals(Optional.empty(), baseline.percent().apply("Unknown"));
    }
}
