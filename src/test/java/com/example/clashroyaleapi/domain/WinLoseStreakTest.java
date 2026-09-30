package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 対戦履歴は新しい順。各テストの List.of の先頭が一番新しい対戦。 */
class WinLoseStreakTest {

    @Test
    void 新しい方から同じ結果が続いた回数を数える() {
        assertEquals(new WinLoseStreak(true, 2, false), streakOf(win(), win(), lose(), win()));
        assertEquals(new WinLoseStreak(false, 1, false), streakOf(lose(), win(), win()));
    }

    @Test
    void フレンドバトルと船のバトルの守備側は飛ばして数える() {
        assertEquals(new WinLoseStreak(true, 2, false), streakOf(
                win(),
                battle("clanMate", null, 0, 3),
                battle("boatBattle", "defender", 0, 3),
                battle("friendly", null, 0, 1),
                win(),
                lose()));
    }

    @Test
    void ランク戦やクラン対戦や船のバトルの攻撃側も数える() {
        assertEquals(new WinLoseStreak(true, 3, false), streakOf(
                battle("pathOfLegend", null, 1, 0),
                battle("riverRacePvP", null, 2, 1),
                battle("boatBattle", "attacker", 3, 0),
                lose()));
    }

    @Test
    void 取得できた対戦がすべて同じ結果なら以上の印を付ける() {
        assertEquals(new WinLoseStreak(true, 3, true), streakOf(win(), win(), win()));
        // 除いた対戦は「すべて」かどうかの判定にも入れない。
        assertEquals(new WinLoseStreak(false, 1, true), streakOf(lose(), battle("friendly", null, 3, 0)));
    }

    @Test
    void 引き分けで途切れる() {
        assertEquals(new WinLoseStreak(true, 1, false), streakOf(win(), draw(), win()));
        assertTrue(WinLoseStreak.from(List.of(draw(), win(), win())).isEmpty());
    }

    @Test
    void 数えられる対戦が無ければ出さない() {
        assertTrue(WinLoseStreak.from(List.of()).isEmpty());
        assertTrue(WinLoseStreak.from(List.of(battle("friendly", null, 1, 0))).isEmpty());
    }

    @Test
    void 片側が欠けた対戦は飛ばす() {
        BattleLogEntry broken = new BattleLogEntry("PvP", "20260101T000000.000Z", null, List.of(), List.of(), null);

        assertEquals(new WinLoseStreak(false, 1, false), streakOf(broken, lose(), win()));
    }

    @Test
    void メッセージキーの末尾は向きと以上の印で決まる() {
        assertEquals("win", new WinLoseStreak(true, 3, false).code());
        assertEquals("lose", new WinLoseStreak(false, 3, false).code());
        assertEquals("winAtLeast", new WinLoseStreak(true, 3, true).code());
        assertEquals("loseAtLeast", new WinLoseStreak(false, 3, true).code());
    }

    private static WinLoseStreak streakOf(BattleLogEntry... battles) {
        return WinLoseStreak.from(List.of(battles)).orElseThrow();
    }

    private static BattleLogEntry win() {
        return battle("PvP", null, 1, 0);
    }

    private static BattleLogEntry lose() {
        return battle("PvP", null, 0, 1);
    }

    private static BattleLogEntry draw() {
        return battle("PvP", null, 1, 1);
    }

    private static BattleLogEntry battle(String type, String boatBattleSide, int selfCrowns, int opponentCrowns) {
        return new BattleLogEntry(type, "20260101T000000.000Z", null,
                List.of(new BattleLogEntry.Participant("#SELF", "Self", selfCrowns, List.of(), List.of())),
                List.of(new BattleLogEntry.Participant("#OPP", "Opponent", opponentCrowns, List.of(), List.of())),
                boatBattleSide);
    }
}
