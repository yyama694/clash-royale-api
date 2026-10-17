package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElixirLeakTest {

    private static BattleLogEntry battle(String type, String mode, int myCrowns, int opponentCrowns, Double leaked) {
        return new BattleLogEntry(type, "20261017T120000.000Z", new BattleLogEntry.GameMode(mode),
                List.of(new BattleLogEntry.Participant("#ME", "me", myCrowns, List.of(), List.of(), null, leaked)),
                List.of(new BattleLogEntry.Participant("#OP", "op", opponentCrowns, List.of(), List.of(), null, 9.9)),
                null);
    }

    private static BattleLogEntry ranked(int myCrowns, int opponentCrowns, double leaked) {
        return battle("pathOfLegend", "Ranked1v1_NewArena", myCrowns, opponentCrowns, leaked);
    }

    @Test
    void 勝った対戦と負けた対戦それぞれの中央値() {
        ElixirLeak leak = ElixirLeak.ofPlayer(List.of(
                ranked(1, 0, 1.0), ranked(2, 0, 5.0), ranked(3, 0, 2.0),
                ranked(0, 1, 4.0), ranked(0, 2, 8.0), ranked(0, 3, 6.0), ranked(1, 2, 80.0)));

        assertEquals(7, leak.battles());
        assertEquals(2.0, leak.winMedian());
        // 偶数個は真ん中の2つの平均。放置とみられる対戦(80)に引きずられない。
        assertEquals(7.0, leak.loseMedian());
        assertTrue(leak.hasMedian());
    }

    @Test
    void トロフィー目標はtypeによらず数え_イベント戦と2v2と引き分けは数えない() {
        List<BattleLogEntry> battleLog = new ArrayList<>(List.of(
                battle("PvP", "Ladder", 1, 0, 1.0),
                battle("trail", "Ladder", 1, 0, 2.0),
                battle("pathOfLegend", "Ranked1v1_NewArena2", 1, 0, 3.0),
                battle("trail", "TripleElixir_Ladder", 1, 0, 40.0),
                battle("friendly", "7xElixir_Friendly", 1, 0, 131.0),
                ranked(1, 1, 0.5)));
        battleLog.add(new BattleLogEntry("pathOfLegend", "20261017T120000.000Z", null,
                List.of(new BattleLogEntry.Participant("#ME", "me", 1, List.of(), List.of(), null, 50.0),
                        new BattleLogEntry.Participant("#MATE", "mate", 1, List.of(), List.of(), null, 50.0)),
                List.of(new BattleLogEntry.Participant("#OP", "op", 0, List.of(), List.of(), null, 1.0),
                        new BattleLogEntry.Participant("#OP2", "op2", 0, List.of(), List.of(), null, 1.0)),
                null));

        ElixirLeak leak = ElixirLeak.ofPlayer(battleLog);

        assertEquals(3, leak.battles());
        assertEquals(2.0, leak.winMedian());
        assertNull(leak.loseMedian());
    }

    @Test
    void 勝ちも負けも3戦に満たなければ中央値を出さない() {
        ElixirLeak leak = ElixirLeak.ofPlayer(List.of(ranked(1, 0, 1.0), ranked(1, 0, 2.0), ranked(0, 1, 3.0)));

        assertEquals(3, leak.battles());
        assertNull(leak.winMedian());
        assertNull(leak.loseMedian());
        assertFalse(leak.hasMedian());
    }

    @Test
    void 世界上位どうしの対戦は1戦につき勝った側と負けた側を1人ずつ数える() {
        Instant time = Instant.parse("2026-10-17T12:00:00Z");
        List<TopBattle> battles = List.of(
                topBattle(time, 1, 0, 1.0, 3.0),
                topBattle(time.plusSeconds(1), 0, 2, 7.0, 2.0),
                topBattle(time.plusSeconds(2), 3, 1, 3.0, 5.0),
                topBattle(time.plusSeconds(3), 1, 1, 50.0, 50.0));

        ElixirLeak leak = ElixirLeak.ofTopBattles(battles);

        assertEquals(3, leak.battles());
        assertEquals(2.0, leak.winMedian());
        assertEquals(5.0, leak.loseMedian());
    }

    private static TopBattle topBattle(Instant time, int firstCrowns, int secondCrowns, double firstLeaked,
            double secondLeaked) {
        return new TopBattle(time, new TopBattle.Side("#A", List.of(1), firstCrowns, firstLeaked),
                new TopBattle.Side("#B", List.of(2), secondCrowns, secondLeaked));
    }
}
