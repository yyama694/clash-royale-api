package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerBattleStatsTest {

    @Test
    void 勝ち負け引き分けをクラウン数から判定して集計する() {
        List<BattleLogEntry> log = List.of(
                battle(3, 0, "Knight"),
                battle(0, 3, "Knight"),
                battle(1, 1, "Knight")
        );

        PlayerBattleStats stats = PlayerBattleStats.from(log);

        assertEquals(3, stats.total());
        assertEquals(1, stats.wins());
        assertEquals(1, stats.losses());
        assertEquals(1, stats.draws());
    }

    @Test
    void カード集計は自分ではなく対戦相手が使用したカードを対象にする() {
        // battle()ヘルパーは自分側にカードを一切持たせていないため、
        // 集計結果に現れるカードは必ず「相手が使ったカード」であることの確認になる。
        List<BattleLogEntry> log = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            log.add(battle(3, 0, "Knight"));
            log.add(battle(0, 3, "Golem"));
        }

        PlayerBattleStats stats = PlayerBattleStats.from(log);

        assertEquals("Knight", stats.favoriteCards().get(0).cardName());
        assertEquals("Golem", stats.weakCards().get(0).cardName());
    }

    @Test
    void 得意カードと苦手カードに同じカードが重複して並ばない() {
        List<BattleLogEntry> log = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            log.add(battle(3, 0, "Knight"));
        }
        for (int i = 0; i < 5; i++) {
            log.add(battle(0, 3, "Golem"));
        }

        PlayerBattleStats stats = PlayerBattleStats.from(log);

        List<String> favorite = stats.favoriteCards().stream().map(PlayerBattleStats.CardPerformance::cardName).toList();
        List<String> weak = stats.weakCards().stream().map(PlayerBattleStats.CardPerformance::cardName).toList();
        assertFalse(favorite.isEmpty());
        assertFalse(weak.isEmpty());
        assertTrue(favorite.stream().noneMatch(weak::contains),
                "得意カード" + favorite + " と苦手カード" + weak + " に同じカードが含まれている");
    }

    @Test
    void 母数が足りない場合は得意苦手を出さない() {
        // カードが1種類しかないと「得意」と「苦手」を区別する意味がないため、どちらも空にする。
        PlayerBattleStats stats = PlayerBattleStats.from(List.of(battle(3, 0, "Knight")));

        assertTrue(stats.favoriteCards().isEmpty());
        assertTrue(stats.weakCards().isEmpty());
        assertEquals(1, stats.total());
    }

    @Test
    void 対戦数が多いカードが少数回の全勝カードより上位に来る() {
        List<BattleLogEntry> log = new ArrayList<>();
        // Knight: 10戦9勝(勝率90%)。試行回数が多く信頼できる。
        for (int i = 0; i < 9; i++) {
            log.add(battle(3, 0, "Knight"));
        }
        log.add(battle(0, 3, "Knight"));
        // Bats: 5戦5勝(勝率100%)。単純な勝率順ならこちらが1位になってしまう。
        for (int i = 0; i < 5; i++) {
            log.add(battle(3, 0, "Bats"));
        }
        // 苦手側の母数を確保するためのカード。
        for (int i = 0; i < 6; i++) {
            log.add(battle(0, 3, "Golem"));
        }

        PlayerBattleStats stats = PlayerBattleStats.from(log);

        assertEquals("Knight", stats.favoriteCards().get(0).cardName());
        assertEquals(90, stats.favoriteCards().get(0).winRatePercent());
    }

    @Test
    void 少数回の全敗カードより試行回数の多い低勝率カードを苦手として優先する() {
        List<BattleLogEntry> log = new ArrayList<>();
        // Golem: 10戦1勝(勝率10%)
        log.add(battle(3, 0, "Golem"));
        for (int i = 0; i < 9; i++) {
            log.add(battle(0, 3, "Golem"));
        }
        // Bats: 5戦0勝(勝率0%)だが母数が少ない
        for (int i = 0; i < 5; i++) {
            log.add(battle(0, 3, "Bats"));
        }
        // 得意側の母数を確保するためのカード。
        for (int i = 0; i < 6; i++) {
            log.add(battle(3, 0, "Knight"));
        }

        PlayerBattleStats stats = PlayerBattleStats.from(log);

        assertEquals("Golem", stats.weakCards().get(0).cardName());
    }

    @Test
    void 使用回数が5回に満たないカードは勝率が高くても順位付けの対象外にする() {
        List<BattleLogEntry> log = new ArrayList<>();
        // Bats: 4戦4勝。Wilson score の下限でも Knight より上になるため、閾値で除外されない限り1位になる。
        for (int i = 0; i < 4; i++) {
            log.add(battle(3, 0, "Bats"));
        }
        // Knight: 7戦5勝
        for (int i = 0; i < 5; i++) {
            log.add(battle(3, 0, "Knight"));
        }
        for (int i = 0; i < 2; i++) {
            log.add(battle(0, 3, "Knight"));
        }
        // 苦手側の母数を確保するためのカード。
        for (int i = 0; i < 5; i++) {
            log.add(battle(0, 3, "Golem"));
        }

        PlayerBattleStats stats = PlayerBattleStats.from(log);

        assertEquals("Knight", stats.favoriteCards().get(0).cardName());
    }

    @Test
    void 二対二の対戦では相手二人分のデッキを集計する() {
        BattleLogEntry.Participant self = participant("#SELF", "Self", 3, List.of());
        BattleLogEntry.Participant mate = participant("#MATE", "Mate", 3, List.of());
        BattleLogEntry.Participant opponent1 = participant("#OPP1", "Opponent1", 0, List.of(card("Knight")));
        BattleLogEntry.Participant opponent2 = participant("#OPP2", "Opponent2", 0, List.of(card("Golem")));
        BattleLogEntry duel = new BattleLogEntry("PvP", "20260101T000000.000Z",
                new BattleLogEntry.GameMode("CasualDuel2v2"), List.of(self, mate), List.of(opponent1, opponent2));
        // 最低使用回数(5回)に届かせるため、同じ対戦を5回分並べる。
        List<BattleLogEntry> log = List.of(duel, duel, duel, duel, duel);

        PlayerBattleStats stats = PlayerBattleStats.from(log);

        assertEquals(5, stats.wins());
        // 相手2人分のカードが両方とも集計に載っていれば、得意/苦手が1枚ずつ選ばれる。
        assertEquals(1, stats.favoriteCards().size());
        assertEquals(1, stats.weakCards().size());
    }

    @Test
    void 使用回数が5回に届くカードが無ければ得意苦手を出さない() {
        // 以前は全カードにフォールバックしており、対戦1件だと相手の8枚がすべて同じ勝敗になるため、
        // 勝率100%のカードが「苦手」にも並んでいた。
        BattleLogEntry.Participant self = participant("#SELF", "Self", 3, List.of());
        BattleLogEntry.Participant opponent = participant("#OPP", "Opponent", 0, List.of(
                card("Knight"), card("Golem"), card("Bats"), card("Zap"),
                card("Arrows"), card("Giant"), card("Miner"), card("Log")));
        BattleLogEntry single = new BattleLogEntry("PvP", "20260101T000000.000Z",
                new BattleLogEntry.GameMode("Ladder"), List.of(self), List.of(opponent));

        PlayerBattleStats stats = PlayerBattleStats.from(List.of(single));

        assertEquals(1, stats.total());
        assertTrue(stats.favoriteCards().isEmpty());
        assertTrue(stats.weakCards().isEmpty());
    }

    @Test
    void チームや相手が空の対戦は集計対象から除外する() {
        BattleLogEntry broken = new BattleLogEntry("PvP", "20260101T000000.000Z",
                new BattleLogEntry.GameMode("Ladder"), List.of(), List.of());

        PlayerBattleStats stats = PlayerBattleStats.from(List.of(broken, battle(3, 0, "Knight")));

        assertEquals(1, stats.total());
        assertEquals(1, stats.wins());
    }

    private static BattleLogEntry battle(int selfCrowns, int opponentCrowns, String opponentCardName) {
        BattleLogEntry.Participant self = participant("#SELF", "Self", selfCrowns, List.of());
        BattleLogEntry.Participant opponent =
                participant("#OPP", "Opponent", opponentCrowns, List.of(card(opponentCardName)));
        return new BattleLogEntry("PvP", "20260101T000000.000Z",
                new BattleLogEntry.GameMode("Ladder"), List.of(self), List.of(opponent));
    }

    private static BattleLogEntry.Participant participant(String tag, String name, int crowns,
            List<BattleLogEntry.Card> cards) {
        return new BattleLogEntry.Participant(tag, name, crowns, cards, List.of());
    }

    private static BattleLogEntry.Card card(String name) {
        return new BattleLogEntry.Card(name, 11, 16, null);
    }
}
