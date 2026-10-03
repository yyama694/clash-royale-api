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

        assertFalse(stats.cardRanking());
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
                new BattleLogEntry.GameMode("CasualDuel2v2"), List.of(self, mate), List.of(opponent1, opponent2), null);
        // 最低使用回数(5回)に届かせるため、同じ対戦を5回分並べる。負け側のカードも2枚あると得意を2枚まで選べる。
        List<BattleLogEntry> log = new ArrayList<>(List.of(duel, duel, duel, duel, duel));
        for (int i = 0; i < 5; i++) {
            log.add(battle(0, 3, "Arrows"));
            log.add(battle(0, 3, "Zap"));
        }

        PlayerBattleStats stats = PlayerBattleStats.from(log);

        assertEquals(5, stats.wins());
        // 相手2人分のカードが両方とも集計に載っていれば、どちらも得意に選ばれる。
        assertEquals(List.of("Golem", "Knight"), stats.favoriteCards().stream()
                .map(PlayerBattleStats.CardPerformance::cardName).sorted().toList());
    }

    @Test
    void 全体の勝率以上のカードは苦手に出さない() {
        // 勝率の高い人は、相対順位だけで選ぶと勝率100%のカードまで「苦手」に並んでいた(2026-09-30のファクトチェック)。
        List<BattleLogEntry> log = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            log.add(battle(3, 0, "Knight"));
        }
        for (int i = 0; i < 5; i++) {
            log.add(battle(3, 0, "Bats"));
            log.add(battle(3, 0, "Zap"));
        }
        // Golem: 5戦4勝(80%)。全体の勝率(24/25=96%)より低いのはこのカードだけ。
        for (int i = 0; i < 4; i++) {
            log.add(battle(3, 0, "Golem"));
        }
        log.add(battle(0, 3, "Golem"));

        PlayerBattleStats stats = PlayerBattleStats.from(log);

        assertTrue(stats.cardRanking());
        assertEquals(2, stats.favoriteCards().size());
        assertEquals(List.of("Golem"), stats.weakCards().stream().map(PlayerBattleStats.CardPerformance::cardName)
                .toList());
    }

    @Test
    void 全体の勝率より低いカードが無ければ苦手は空にする() {
        List<BattleLogEntry> log = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            log.add(battle(3, 0, "Knight"));
            log.add(battle(3, 0, "Golem"));
        }
        // 負けた対戦の相手のカードは5回に届かないので、得意/苦手の候補にならない。
        log.add(battle(0, 3, "Bats"));
        log.add(battle(0, 3, "Bats"));

        PlayerBattleStats stats = PlayerBattleStats.from(log);

        assertTrue(stats.cardRanking());
        assertEquals(1, stats.favoriteCards().size());
        assertTrue(stats.weakCards().isEmpty());
    }

    @Test
    void 全体の勝率と同じカードは得意にも苦手にも出さない() {
        List<BattleLogEntry> log = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            log.add(battle(3, 0, "Knight"));
            log.add(battle(3, 0, "Golem"));
        }

        PlayerBattleStats stats = PlayerBattleStats.from(log);

        assertTrue(stats.cardRanking());
        assertTrue(stats.favoriteCards().isEmpty());
        assertTrue(stats.weakCards().isEmpty());
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
                new BattleLogEntry.GameMode("Ladder"), List.of(self), List.of(opponent), null);

        PlayerBattleStats stats = PlayerBattleStats.from(List.of(single));

        assertEquals(1, stats.total());
        assertTrue(stats.favoriteCards().isEmpty());
        assertTrue(stats.weakCards().isEmpty());
    }

    @Test
    void チームや相手が空の対戦は集計対象から除外する() {
        BattleLogEntry broken = new BattleLogEntry("PvP", "20260101T000000.000Z",
                new BattleLogEntry.GameMode("Ladder"), List.of(), List.of(), null);

        PlayerBattleStats stats = PlayerBattleStats.from(List.of(broken, battle(3, 0, "Knight")));

        assertEquals(1, stats.total());
        assertEquals(1, stats.wins());
    }

    @Test
    void フレンドバトルは勝敗にもカードの集計にも入れず除いた数だけ残す() {
        List<BattleLogEntry> log = new ArrayList<>();
        log.add(battle(3, 0, "Knight"));
        // 同じクランの相手と続けて戦ったフレンドバトル。数えると Golem が苦手カードに並んでしまう。
        for (int i = 0; i < 5; i++) {
            log.add(battle("clanMate", "Friendly", 0, 3, "Golem"));
        }
        log.add(battle("friendly", "Friendly", 0, 1, "Golem"));

        PlayerBattleStats stats = PlayerBattleStats.from(log);

        assertEquals(1, stats.total());
        assertEquals(1, stats.wins());
        assertEquals(0, stats.losses());
        assertEquals(6, stats.friendlyExcluded());
        assertTrue(stats.weakCards().isEmpty());
    }

    @Test
    void 名前にFriendlyと付く特殊ルール戦でも対戦の種類がフレンドバトルでなければ数える() {
        // RR_*_Friendly は type が unknown で、相手は他クランのプレイヤー(2026-09-27に実データで確認)。
        PlayerBattleStats stats = PlayerBattleStats.from(List.of(battle("unknown", "RR_Rage_Friendly", 3, 0, "Knight")));

        assertEquals(1, stats.total());
        assertEquals(0, stats.friendlyExcluded());
    }

    @Test
    void 船のバトルの守備側は勝敗にもカードの集計にも入れず除いた数だけ残す() {
        List<BattleLogEntry> log = new ArrayList<>();
        log.add(battle(3, 0, "Knight"));
        // 船の防衛設備が戦った対戦。放置していても負けが並ぶ。
        for (int i = 0; i < 5; i++) {
            log.add(boatBattle("defender", 0, 3, "Golem"));
        }
        log.add(boatBattle("attacker", 3, 0, "Knight"));

        PlayerBattleStats stats = PlayerBattleStats.from(log);

        assertEquals(2, stats.total());
        assertEquals(2, stats.wins());
        assertEquals(0, stats.losses());
        assertEquals(5, stats.boatDefenseExcluded());
        assertEquals(0, stats.friendlyExcluded());
        assertTrue(stats.weakCards().isEmpty());
    }

    @Test
    void すべてフレンドバトルなら集計する対戦は0件になる() {
        PlayerBattleStats stats = PlayerBattleStats.from(List.of(battle("clanMate", "Friendly", 3, 0, "Knight")));

        assertEquals(0, stats.total());
        assertEquals(1, stats.friendlyExcluded());
        assertTrue(stats.favoriteCards().isEmpty());
    }

    private static BattleLogEntry battle(int selfCrowns, int opponentCrowns, String opponentCardName) {
        return battle("PvP", "Ladder", selfCrowns, opponentCrowns, opponentCardName);
    }

    private static BattleLogEntry battle(String type, String gameMode, int selfCrowns, int opponentCrowns,
            String opponentCardName) {
        BattleLogEntry.Participant self = participant("#SELF", "Self", selfCrowns, List.of());
        BattleLogEntry.Participant opponent =
                participant("#OPP", "Opponent", opponentCrowns, List.of(card(opponentCardName)));
        return new BattleLogEntry(type, "20260101T000000.000Z",
                new BattleLogEntry.GameMode(gameMode), List.of(self), List.of(opponent), null);
    }

    private static BattleLogEntry boatBattle(String side, int selfCrowns, int opponentCrowns, String opponentCardName) {
        BattleLogEntry.Participant self = participant("#SELF", "Self", selfCrowns, List.of());
        BattleLogEntry.Participant opponent =
                participant("#OPP", "Opponent", opponentCrowns, List.of(card(opponentCardName)));
        return new BattleLogEntry("boatBattle", "20260101T000000.000Z",
                new BattleLogEntry.GameMode("ClanWar_BoatBattle"), List.of(self), List.of(opponent), side);
    }

    private static BattleLogEntry.Participant participant(String tag, String name, int crowns,
            List<BattleLogEntry.Card> cards) {
        return new BattleLogEntry.Participant(tag, name, crowns, cards, List.of());
    }

    // カードIDは名前ごとに一意であればよいので、名前のハッシュから作る(詳細画面へのリンクに使う値)。
    private static BattleLogEntry.Card card(String name) {
        return new BattleLogEntry.Card(name.hashCode(), name, 11, 16, 3, null, null);
    }
}
