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
    void そのカード入りのデッキに全体より勝率が高かったカードを出す() {
        List<TopBattle> battles = new ArrayList<>();
        // 11は両方のデッキにあるので、11と1の組は数えない。
        add(battles, 150, List.of(10, 11), List.of(1, 11), 120);
        add(battles, 150, List.of(20), List.of(1), 60);
        // 100戦に満たない組み合わせは、勝率が高くても出さない。
        add(battles, 99, List.of(30), List.of(1), 99);
        // 引き分けは数えない。
        for (int i = 0; i < 10; i++) {
            battles.add(new TopBattle(START.plusSeconds(10_000 + i), new TopBattle.Side("#D" + i, List.of(10), 1, 0),
                    new TopBattle.Side("#E" + i, List.of(1), 1, 0)));
        }

        CardMatchups matchups = CardMatchups.of(battles);

        assertEquals(399, matchups.battles());
        assertEquals(START, matchups.from());
        assertEquals(START.plusSeconds(398), matchups.to());
        CardMatchups.Against against = matchups.against(1).orElseThrow();
        assertEquals(399, against.battles());
        assertEquals(279, against.wins());
        assertEquals(70, against.winRatePercent());
        // 20(40%)は全体(70%)より低いので出さない。
        assertEquals(List.of(new CardMatchups.Counter(10, 150, 120)), against.counters());
        assertEquals(80, against.counters().get(0).winRatePercent());
    }

    @Test
    void 両方のデッキに入っていたカードと当たっていないカードは空() {
        List<TopBattle> battles = new ArrayList<>();
        add(battles, 150, List.of(10, 11), List.of(1, 11), 120);

        CardMatchups matchups = CardMatchups.of(battles);

        assertEquals(Optional.empty(), matchups.against(11));
        assertEquals(Optional.empty(), matchups.against(99));
        // 10入りのデッキに勝ったのは1入りのデッキだが、勝率(20%)は全体と同じなので出さない。
        assertTrue(matchups.against(10).orElseThrow().counters().isEmpty());
    }

    @Test
    void 整数の勝率で全体と同じになるカードは出さない() {
        List<TopBattle> battles = new ArrayList<>();
        // 全体は (530 + 70 + 64) / (1000 + 129 + 100) = 664/1229 = 54.03%。
        add(battles, 1000, List.of(90), List.of(1), 530);
        // 70/129 = 54.26% はわずかに全体を上回るが、画面では同じ54%になる。
        add(battles, 129, List.of(40), List.of(1), 70);
        // 64/100 = 64%
        add(battles, 100, List.of(50), List.of(1), 64);

        CardMatchups.Against against = CardMatchups.of(battles).against(1).orElseThrow();

        assertEquals(List.of(50), against.counters().stream().map(CardMatchups.Counter::cardId).toList());
    }

    @Test
    void 対戦数も加味して並べ_5枚までにする() {
        List<TopBattle> battles = new ArrayList<>();
        // 全体の勝率を下げるための対戦。
        add(battles, 1000, List.of(90), List.of(1), 300);
        add(battles, 200, List.of(40), List.of(1), 150);
        add(battles, 100, List.of(50), List.of(1), 76);
        add(battles, 100, List.of(60), List.of(1), 70);
        add(battles, 100, List.of(61), List.of(1), 69);
        add(battles, 100, List.of(62), List.of(1), 68);
        add(battles, 100, List.of(63), List.of(1), 67);

        List<CardMatchups.Counter> counters = CardMatchups.of(battles).against(1).orElseThrow().counters();

        // 50は勝率76%で40(75%)より高いが、対戦数が半分なので後ろに来る。
        assertEquals(List.of(40, 50, 60, 61, 62), counters.stream().map(CardMatchups.Counter::cardId).toList());
    }
}
