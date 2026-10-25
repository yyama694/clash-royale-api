package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardMatchupsTest {

    private static final Instant START = Instant.parse("2026-10-11T00:00:00Z");

    /** first 側のデッキと second 側のデッキが count 回当たり、first 側が firstWins 回勝った(残りは負け)。 */
    private static void add(List<TopBattle> battles, int count, List<Integer> first, List<Integer> second,
            int firstWins) {
        for (int i = 0; i < count; i++) {
            int n = battles.size();
            boolean firstWon = i < firstWins;
            battles.add(new TopBattle(START.plusSeconds(n),
                    new TopBattle.Side("#A" + n, first, firstWon ? 1 : 0, 2.0),
                    new TopBattle.Side("#B" + n, second, firstWon ? 0 : 1, 3.0)));
        }
    }

    @Test
    void そのカード入りのデッキの勝率が全体より高い相手を得意_低い相手を苦手に出す() {
        List<TopBattle> battles = new ArrayList<>();
        // 11は両方のデッキにあるので、1と11の組は数えない。
        add(battles, 150, List.of(1, 11), List.of(10, 11), 30);
        add(battles, 150, List.of(1), List.of(20), 90);
        // 100戦に満たない組み合わせは、勝率が高くても低くても出さない。
        add(battles, 99, List.of(1), List.of(30), 0);
        // 引き分けは数えない。
        for (int i = 0; i < 10; i++) {
            battles.add(new TopBattle(START.plusSeconds(10_000 + i), new TopBattle.Side("#D" + i, List.of(1), 1, 0),
                    new TopBattle.Side("#E" + i, List.of(10), 1, 0)));
        }

        CardMatchups matchups = CardMatchups.of(battles);

        assertEquals(399, matchups.battles());
        assertEquals(START, matchups.from());
        assertEquals(START.plusSeconds(398), matchups.to());
        CardMatchups.Stats stats = matchups.decksWith(1).orElseThrow();
        assertEquals(399, stats.battles());
        assertEquals(120, stats.wins());
        assertEquals(30, stats.winRatePercent());
        // 勝率はどちらも1入りのデッキの側から見たもの。
        assertEquals(List.of(new CardMatchups.Opponent(20, 150, 90)), stats.favorable());
        assertEquals(60, stats.favorable().get(0).winRatePercent());
        assertEquals(List.of(new CardMatchups.Opponent(10, 150, 30)), stats.unfavorable());
        assertEquals(20, stats.unfavorable().get(0).winRatePercent());
    }

    @Test
    void 相手の側から見ると得意と苦手が入れ替わる() {
        List<TopBattle> battles = new ArrayList<>();
        add(battles, 150, List.of(1), List.of(10), 30);
        add(battles, 150, List.of(1), List.of(20), 90);
        add(battles, 150, List.of(2), List.of(10), 120);

        CardMatchups matchups = CardMatchups.of(battles);

        // 10入りのデッキは、1入りに 120/150、2入りに 30/150 勝った(全体 150/300 = 50%)。
        CardMatchups.Stats stats = matchups.decksWith(10).orElseThrow();
        assertEquals(List.of(new CardMatchups.Opponent(1, 150, 120)), stats.favorable());
        assertEquals(List.of(new CardMatchups.Opponent(2, 150, 30)), stats.unfavorable());
    }

    @Test
    void 両方のデッキに入っていたカードと当たっていないカードは空() {
        List<TopBattle> battles = new ArrayList<>();
        add(battles, 150, List.of(10, 11), List.of(1, 11), 120);

        CardMatchups matchups = CardMatchups.of(battles);

        assertEquals(Optional.empty(), matchups.decksWith(11));
        assertEquals(Optional.empty(), matchups.decksWith(99));
        // 10入りのデッキが当たったのは1入りのデッキだけで、その勝率(80%)は全体と同じなので、どちらにも出さない。
        CardMatchups.Stats stats = matchups.decksWith(10).orElseThrow();
        assertTrue(stats.favorable().isEmpty());
        assertTrue(stats.unfavorable().isEmpty());
    }

    @Test
    void 整数の勝率で全体と同じになるカードは出さない() {
        List<TopBattle> battles = new ArrayList<>();
        // 1入りの全体は (530 + 70 + 64 + 36 + 105) / (1000 + 129 + 100 + 100 + 200) = 805/1529 = 52.65%。
        // 530/1000 = 53% はわずかに全体を上回るが、画面では同じ53%になる。
        add(battles, 1000, List.of(1), List.of(90), 530);
        // 70/129 = 54.26%
        add(battles, 129, List.of(1), List.of(40), 70);
        // 64/100 = 64%
        add(battles, 100, List.of(1), List.of(50), 64);
        // 36/100 = 36%
        add(battles, 100, List.of(1), List.of(60), 36);
        // 105/200 = 52.5% はわずかに全体を下回るが、画面では同じ53%になる。
        add(battles, 200, List.of(1), List.of(70), 105);

        CardMatchups.Stats stats = CardMatchups.of(battles).decksWith(1).orElseThrow();

        assertEquals(53, stats.winRatePercent());
        assertEquals(List.of(50, 40), stats.favorable().stream().map(CardMatchups.Opponent::cardId).toList());
        assertEquals(List.of(60), stats.unfavorable().stream().map(CardMatchups.Opponent::cardId).toList());
    }

    @Test
    void 対戦数も加味して並べ_5枚までにする() {
        List<TopBattle> battles = new ArrayList<>();
        // 全体の勝率を下げるための対戦。
        add(battles, 1000, List.of(1), List.of(90), 300);
        add(battles, 200, List.of(1), List.of(40), 150);
        add(battles, 100, List.of(1), List.of(50), 76);
        add(battles, 100, List.of(1), List.of(60), 70);
        add(battles, 100, List.of(1), List.of(61), 69);
        add(battles, 100, List.of(1), List.of(62), 68);
        add(battles, 100, List.of(1), List.of(63), 67);

        List<CardMatchups.Opponent> favorable = CardMatchups.of(battles).decksWith(1).orElseThrow().favorable();

        // 50は勝率76%で40(75%)より高いが、対戦数が半分なので後ろに来る。
        assertEquals(List.of(40, 50, 60, 61, 62), favorable.stream().map(CardMatchups.Opponent::cardId).toList());
    }

    @Test
    void 苦手は勝率の低い方から対戦数も加味して並べる() {
        List<TopBattle> battles = new ArrayList<>();
        // 全体の勝率を上げるための対戦。
        add(battles, 1000, List.of(1), List.of(90), 700);
        add(battles, 200, List.of(1), List.of(40), 50);
        add(battles, 100, List.of(1), List.of(50), 24);
        add(battles, 100, List.of(1), List.of(60), 30);

        List<CardMatchups.Opponent> unfavorable = CardMatchups.of(battles).decksWith(1).orElseThrow().unfavorable();

        // 50は勝率24%で40(25%)より低いが、対戦数が半分なので後ろに来る。
        assertEquals(List.of(40, 50, 60), unfavorable.stream().map(CardMatchups.Opponent::cardId).toList());
    }
}
