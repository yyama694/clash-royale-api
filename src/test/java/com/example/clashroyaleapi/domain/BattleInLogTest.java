package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleInLogTest {

    private static final BattleLogEntry.Participant SELF =
            new BattleLogEntry.Participant("#SELF", "Self", 3, List.of(), List.of(), null);
    private static final BattleLogEntry.Participant OPPONENT =
            new BattleLogEntry.Participant("#OPP", "Opp", 0, List.of(), List.of(), null);

    @Test
    void 一覧で隣にある古い対戦と新しい対戦を返す() {
        BattleInLog found = BattleInLog.find(List.of(battle("T3"), battle("T2"), battle("T1")), "T2").orElseThrow();

        assertEquals("T2", found.battle().battleTime());
        assertEquals("T1", found.olderBattleTime());
        assertEquals("T3", found.newerBattleTime());
    }

    @Test
    void 端の対戦は片側が無い() {
        List<BattleLogEntry> log = List.of(battle("T2"), battle("T1"));

        assertNull(BattleInLog.find(log, "T2").orElseThrow().newerBattleTime());
        assertNull(BattleInLog.find(log, "T1").orElseThrow().olderBattleTime());
    }

    @Test
    void 一覧に出さない片側が欠けた対戦は飛ばす() {
        BattleLogEntry oneSided = new BattleLogEntry("PvP", "T2", new BattleLogEntry.GameMode("Ladder"),
                List.of(SELF), List.of(), null);

        BattleInLog found = BattleInLog.find(List.of(battle("T3"), oneSided, battle("T1")), "T3").orElseThrow();

        assertEquals("T1", found.olderBattleTime());
        assertTrue(BattleInLog.find(List.of(battle("T3"), oneSided), "T2").isEmpty());
    }

    private static BattleLogEntry battle(String battleTime) {
        return new BattleLogEntry("PvP", battleTime, new BattleLogEntry.GameMode("Ladder"),
                List.of(SELF), List.of(OPPONENT), null);
    }
}
