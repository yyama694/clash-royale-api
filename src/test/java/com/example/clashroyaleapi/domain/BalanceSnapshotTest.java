package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BalanceSnapshotTest {

    @Test
    void 手で残した使用率を調整の実施日ごとに読み_書いていないカードと進化は空() {
        Map<LocalDate, BalanceSnapshot> snapshots = BalanceSnapshot.parse(List.of(
                "# コメント",
                "2026-10-06\t2026-09-30\t981\tBarbarian Barrel\t38.9",
                "2026-10-06\t2026-09-30\t981\tSkeletons\t35.9"));

        BalanceSnapshot october = snapshots.get(LocalDate.of(2026, 10, 6));
        assertEquals(LocalDate.of(2026, 9, 30), october.collectedOn());
        assertEquals(981, october.sampleSize());
        assertEquals(Optional.of(38.9), october.percentOf("Barbarian Barrel", CardForm.NORMAL));
        assertEquals(Optional.empty(), october.percentOf("Barbarian Barrel", CardForm.EVOLUTION));
        assertEquals(Optional.empty(), october.percentOf("Royal Ghost", CardForm.NORMAL));
    }

    @Test
    void 集計の履歴からは_集計に無いカードを0パーセントにし_一覧に無いカードは空() {
        TopDecks decks = new TopDecks(Instant.parse("2026-11-01T11:00:00Z"), List.of(
                new TopDecks.SampledDeck(List.of(1, 2), null),
                new TopDecks.SampledDeck(List.of(1, 3), null)));

        BalanceSnapshot snapshot = BalanceSnapshot.of(CardUsage.of(decks), Map.of("Knight", 1, "Archers", 9));

        assertEquals(LocalDate.of(2026, 11, 1), snapshot.collectedOn());
        assertEquals(2, snapshot.sampleSize());
        assertEquals(Optional.of(100.0), snapshot.percentOf("Knight", CardForm.NORMAL));
        assertEquals(Optional.of(0.0), snapshot.percentOf("Archers", CardForm.NORMAL));
        assertEquals(Optional.empty(), snapshot.percentOf("Unknown", CardForm.NORMAL));
    }

    @Test
    void 進化とヒーローはその形で使っていた人の割合で_通常は形を問わずデッキに入っていた割合() {
        TopDecks.Player evolved = new TopDecks.Player("#A", "a", 1, 3000, List.of(), null,
                List.of(CardForm.EVOLUTION, CardForm.NORMAL));
        TopDecks.Player hero = new TopDecks.Player("#B", "b", 2, 3000, List.of(), null,
                List.of(CardForm.HERO, CardForm.NORMAL));
        TopDecks decks = new TopDecks(Instant.parse("2026-11-01T11:00:00Z"), List.of(
                new TopDecks.SampledDeck(List.of(1, 2), null, evolved),
                new TopDecks.SampledDeck(List.of(1, 3), null, hero),
                new TopDecks.SampledDeck(List.of(1, 4), null),
                new TopDecks.SampledDeck(List.of(5, 6), null)));

        BalanceSnapshot snapshot = BalanceSnapshot.of(CardUsage.of(decks), Map.of("Knight", 1, "Archers", 2));

        assertEquals(Optional.of(75.0), snapshot.percentOf("Knight", CardForm.NORMAL));
        assertEquals(Optional.of(25.0), snapshot.percentOf("Knight", CardForm.EVOLUTION));
        assertEquals(Optional.of(25.0), snapshot.percentOf("Knight", CardForm.HERO));
        assertEquals(Optional.of(0.0), snapshot.percentOf("Archers", CardForm.EVOLUTION));
    }
}
