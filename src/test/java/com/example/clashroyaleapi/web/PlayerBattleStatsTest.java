package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
        List<BattleLogEntry> log = List.of(battle(3, 0, "Knight"));

        PlayerBattleStats stats = PlayerBattleStats.from(log);

        assertEquals("Knight", stats.favoriteCards().get(0).cardName());
    }

    @Test
    void 使用回数が最低ライン未満のカードしかない場合は全カードにフォールバックする() {
        // MIN_USES_FOR_RANKING(5)未満のカードしかない状況。
        List<BattleLogEntry> log = List.of(
                battle(3, 0, "Knight"),
                battle(3, 0, "Golem")
        );

        PlayerBattleStats stats = PlayerBattleStats.from(log);

        assertEquals(2, stats.favoriteCards().size());
        assertEquals(2, stats.weakCards().size());
    }

    @Test
    void 得意カードは相手カードに対する勝率が高い順に並ぶ() {
        List<BattleLogEntry> log = new ArrayList<>();
        // Knight: 5戦4勝(勝率80%)
        log.add(battle(3, 0, "Knight"));
        log.add(battle(3, 0, "Knight"));
        log.add(battle(3, 0, "Knight"));
        log.add(battle(3, 0, "Knight"));
        log.add(battle(0, 3, "Knight"));
        // Golem: 5戦1勝(勝率20%)
        log.add(battle(3, 0, "Golem"));
        log.add(battle(0, 3, "Golem"));
        log.add(battle(0, 3, "Golem"));
        log.add(battle(0, 3, "Golem"));
        log.add(battle(0, 3, "Golem"));

        PlayerBattleStats stats = PlayerBattleStats.from(log);

        assertEquals("Knight", stats.favoriteCards().get(0).cardName());
        assertEquals(80, stats.favoriteCards().get(0).winRatePercent());
        assertEquals("Golem", stats.weakCards().get(0).cardName());
        assertEquals(20, stats.weakCards().get(0).winRatePercent());
    }

    @Test
    void 勝率が同じ場合は使用回数が多いカードを上位にする() {
        List<BattleLogEntry> log = new ArrayList<>();
        // A: 6戦3勝(勝率50%)
        for (int i = 0; i < 3; i++) {
            log.add(battle(3, 0, "A"));
        }
        for (int i = 0; i < 3; i++) {
            log.add(battle(0, 3, "A"));
        }
        // B: 8戦4勝(勝率50%、Aより使用回数が多い)
        for (int i = 0; i < 4; i++) {
            log.add(battle(3, 0, "B"));
        }
        for (int i = 0; i < 4; i++) {
            log.add(battle(0, 3, "B"));
        }

        PlayerBattleStats stats = PlayerBattleStats.from(log);

        assertEquals("B", stats.favoriteCards().get(0).cardName());
        assertEquals("A", stats.favoriteCards().get(1).cardName());
        assertFalse(stats.weakCards().isEmpty());
        assertEquals("B", stats.weakCards().get(0).cardName());
    }

    private static BattleLogEntry battle(int selfCrowns, int opponentCrowns, String opponentCardName) {
        BattleLogEntry.Participant self = new BattleLogEntry.Participant("#SELF", "Self", selfCrowns, List.of(), List.of());
        BattleLogEntry.Participant opponent = new BattleLogEntry.Participant(
                "#OPP", "Opponent", opponentCrowns, List.of(card(opponentCardName)), List.of());
        return new BattleLogEntry("PvP", "20260101T000000.000Z",
                new BattleLogEntry.GameMode("Ladder"), List.of(self), List.of(opponent));
    }

    private static BattleLogEntry.Card card(String name) {
        return new BattleLogEntry.Card(name, 11, null);
    }
}
