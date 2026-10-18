package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleResultTest {

    @Test
    void クラウン数の比較で勝敗を決める() {
        assertEquals(BattleResult.WIN, BattleResult.of(3, 1));
        assertEquals(BattleResult.LOSE, BattleResult.of(0, 1));
        assertEquals(BattleResult.DRAW, BattleResult.of(1, 1));
    }

    @Test
    void 対戦の勝敗は各陣営のクラウン数の最大で決める() {
        // 2v2で片方のメンバーにだけクラウン数が入っていても、陣営としての勝敗を出す。
        BattleLogEntry battle = battle(List.of(participant(2), participant(0)), List.of(participant(1)));

        assertEquals(BattleResult.WIN, BattleResult.of(battle));
        assertEquals(2, BattleResult.crownsOf(battle.team()));
    }

    @Test
    void デュエルはクラウンの合計ではなく取ったラウンドの数で勝敗を決める() {
        // 合計は5-4だが、ラウンドは1勝2敗(2026-10-18に本番で逆に出ていた対戦と同じ形)。
        BattleLogEntry lost = clanWarDuel(List.of(3, 1, 1), List.of(0, 2, 2));
        // 合計は4-5だが、ラウンドは2勝1敗。
        BattleLogEntry won = clanWarDuel(List.of(2, 0, 2), List.of(1, 3, 1));

        assertEquals(BattleResult.LOSE, BattleResult.of(lost));
        assertEquals(new BattleResult.Score(1, 2, true), BattleResult.scoreOf(lost));
        assertEquals(BattleResult.WIN, BattleResult.of(won));
        assertEquals(new BattleResult.Score(2, 1, true), BattleResult.scoreOf(won));
    }

    @Test
    void ラウンドの無い対戦のスコアはクラウン数() {
        BattleLogEntry battle = battle(List.of(participant(3)), List.of(participant(1)));

        assertFalse(BattleResult.isDuel(battle));
        assertEquals(new BattleResult.Score(3, 1, false), BattleResult.scoreOf(battle));
    }

    @Test
    void 片側が欠けた対戦は両側がそろっていない扱いにする() {
        assertTrue(BattleResult.hasBothSides(battle(List.of(participant(1)), List.of(participant(0)))));
        assertFalse(BattleResult.hasBothSides(battle(List.of(participant(1)), List.of())));
        assertFalse(BattleResult.hasBothSides(battle(null, List.of(participant(0)))));
        assertFalse(BattleResult.hasBothSides(null));
    }

    @Test
    void 相手側から見た勝敗は勝ちと負けが入れ替わる() {
        assertEquals(BattleResult.LOSE, BattleResult.WIN.opposite());
        assertEquals(BattleResult.WIN, BattleResult.LOSE.opposite());
        assertEquals(BattleResult.DRAW, BattleResult.DRAW.opposite());
    }

    @Test
    void codeはメッセージキーとCSSクラスに使う小文字表記() {
        assertEquals("win", BattleResult.WIN.code());
        assertEquals("lose", BattleResult.LOSE.code());
        assertEquals("draw", BattleResult.DRAW.code());
    }

    private static BattleLogEntry battle(List<BattleLogEntry.Participant> team,
            List<BattleLogEntry.Participant> opponent) {
        return new BattleLogEntry("PvP", "20260101T000000.000Z", new BattleLogEntry.GameMode("Ladder"), team,
                opponent, null);
    }

    private static BattleLogEntry.Participant participant(int crowns) {
        return new BattleLogEntry.Participant("#TAG", "name", crowns, List.of(), List.of(), null);
    }

    /** 公式APIと同じく、各側の crowns は全ラウンドの合計にする。 */
    private static BattleLogEntry clanWarDuel(List<Integer> mine, List<Integer> theirs) {
        return new BattleLogEntry("riverRaceDuel", "20260101T000000.000Z", new BattleLogEntry.GameMode("CW_Duel_1v1"),
                List.of(duelist(mine)), List.of(duelist(theirs)), null);
    }

    private static BattleLogEntry.Participant duelist(List<Integer> crownsByRound) {
        List<BattleLogEntry.Round> rounds = crownsByRound.stream()
                .map(crowns -> new BattleLogEntry.Round(crowns, List.of(), null))
                .toList();
        return new BattleLogEntry.Participant("#TAG", "name", crownsByRound.stream().mapToInt(Integer::intValue).sum(),
                List.of(), List.of(), null, null, rounds);
    }
}
