package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopBattleTest {

    private static List<BattleLogEntry.Card> deck(int firstId, int size) {
        return IntStream.range(firstId, firstId + size)
                .mapToObj(id -> new BattleLogEntry.Card(id, "card", 14, 14, 3, null, null))
                .toList();
    }

    private static BattleLogEntry.Participant participant(String tag, int crowns, List<BattleLogEntry.Card> cards,
            Double leaked) {
        return new BattleLogEntry.Participant(tag, "name", crowns, cards, List.of(), null, leaked);
    }

    private static BattleLogEntry battle(String type, BattleLogEntry.Participant me,
            BattleLogEntry.Participant opponent) {
        return new BattleLogEntry(type, "20261017T120000.000Z", null, List.of(me), List.of(opponent), null);
    }

    @Test
    void 両者の対戦履歴から取った同じ対戦は同じものになる() {
        BattleLogEntry.Participant a = participant("#A", 1, deck(0, 8), 2.5);
        BattleLogEntry.Participant b = participant("#B", 0, deck(100, 8), 6.0);

        TopBattle fromA = TopBattle.of(battle("pathOfLegend", a, b)).orElseThrow();
        TopBattle fromB = TopBattle.of(battle("pathOfLegend", b, a)).orElseThrow();

        assertEquals(fromA, fromB);
        assertEquals(fromA.key(), fromB.key());
        assertEquals(Instant.parse("2026-10-17T12:00:00Z"), fromA.time());
        assertEquals("#A", fromA.first().tag());
        assertEquals("#A", fromA.winner().orElseThrow().tag());
        assertEquals(6.0, fromA.loser().orElseThrow().elixirLeaked());
    }

    @Test
    void 引き分けは勝者も敗者もいない() {
        TopBattle draw = TopBattle.of(battle("pathOfLegend", participant("#A", 1, deck(0, 8), 1.0),
                participant("#B", 1, deck(100, 8), 1.0))).orElseThrow();

        assertEquals(Optional.empty(), draw.winner());
        assertEquals(Optional.empty(), draw.loser());
    }

    @Test
    void ランク戦の1対1で両者の8枚と消費漏れがそろったものだけ() {
        BattleLogEntry.Participant a = participant("#A", 1, deck(0, 8), 2.5);
        BattleLogEntry.Participant b = participant("#B", 0, deck(100, 8), 6.0);

        assertTrue(TopBattle.of(battle("PvP", a, b)).isEmpty());
        assertTrue(TopBattle.of(battle("pathOfLegend", a, participant("#B", 0, deck(100, 7), 6.0))).isEmpty());
        assertTrue(TopBattle.of(battle("pathOfLegend", a, participant("#B", 0, deck(100, 8), null))).isEmpty());
        assertTrue(TopBattle.of(new BattleLogEntry("pathOfLegend", "20261017T120000.000Z", null,
                List.of(a, participant("#C", 1, deck(200, 8), 1.0)),
                List.of(b, participant("#D", 0, deck(300, 8), 1.0)), null)).isEmpty());
        assertTrue(TopBattle.of(new BattleLogEntry("pathOfLegend", "broken", null, List.of(a), List.of(b), null))
                .isEmpty());
    }
}
