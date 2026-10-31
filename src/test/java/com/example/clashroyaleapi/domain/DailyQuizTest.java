package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyQuizTest {

    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 10, 26);

    // 2026-10の実際の分布に近い122枚(1〜9エリクサー)。ヒットポイントは持たせない(エリクサーの問題だけになる)。
    private static final List<DailyQuiz.Card> CARDS = IntStream.range(0, 122)
            .mapToObj(i -> new DailyQuiz.Card(26_000_000 + i, false, cost(i)))
            .toList();

    // 122枚のうち82枚に、ヒットポイント(130〜8230)を持たせたもの。2026-10の並べ替えに出せる枚数と同じ。
    private static final List<DailyQuiz.Card> CARDS_WITH_HEALTH = IntStream.range(0, 122)
            .mapToObj(i -> new DailyQuiz.Card(26_000_000 + i, false, cost(i), i < 82 ? 130 + i * 100 : null))
            .toList();

    private static int cost(int i) {
        int[] costs = {1, 2, 2, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 6, 6, 7, 8, 9};
        return costs[i % costs.length];
    }

    @Test
    void 同じ日付なら同じ問題になる() {
        LocalDate date = LocalDate.of(2026, 11, 3);

        assertEquals(DailyQuiz.of(date, FIRST_DAY, CARDS_WITH_HEALTH), DailyQuiz.of(date, FIRST_DAY, CARDS_WITH_HEALTH));
    }

    @Test
    void 公式APIの並び順が変わっても同じ問題になる() {
        LocalDate date = LocalDate.of(2026, 11, 3);
        List<DailyQuiz.Card> shuffled = new ArrayList<>(CARDS_WITH_HEALTH);
        Collections.reverse(shuffled);

        assertEquals(DailyQuiz.of(date, FIRST_DAY, CARDS_WITH_HEALTH), DailyQuiz.of(date, FIRST_DAY, shuffled));
    }

    @Test
    void 地上の形は同じカードの別の問題になり並び順が変わっても同じ問題になる() {
        DailyQuiz.Card mounted = new DailyQuiz.Card(28_000_025, false, 6);
        DailyQuiz.Card ground = new DailyQuiz.Card(28_000_025, true, 3);
        List<DailyQuiz.Card> cards = new ArrayList<>(CARDS);
        cards.add(mounted);
        cards.add(ground);
        List<DailyQuiz.Card> reversed = new ArrayList<>(cards);
        Collections.reverse(reversed);

        boolean bothSeen = false;
        for (int day = 0; day < 200; day++) {
            LocalDate date = FIRST_DAY.plusDays(day);
            DailyQuiz quiz = DailyQuiz.of(date, FIRST_DAY, cards);
            assertEquals(quiz, DailyQuiz.of(date, FIRST_DAY, reversed));
            for (QuizQuestion.Elixir question : elixirs(quiz)) {
                if (question.cardId() == 28_000_025) {
                    assertEquals(question.ground() ? 3 : 6, question.correctChoice());
                }
            }
            bothSeen |= elixirs(quiz).stream().filter(q -> q.cardId() == 28_000_025).count() == 2;
        }
        // 2つの形は別のカードなので、同じ日に両方出ることもある
        assertTrue(bothSeen);
    }

    @Test
    void 日付が違えば問題が変わる() {
        DailyQuiz today = DailyQuiz.of(LocalDate.of(2026, 11, 3), FIRST_DAY, CARDS_WITH_HEALTH);
        DailyQuiz tomorrow = DailyQuiz.of(LocalDate.of(2026, 11, 4), FIRST_DAY, CARDS_WITH_HEALTH);

        assertNotEquals(cardIds(today), cardIds(tomorrow));
    }

    @Test
    void 一日の30問には並べ替えの4枚も含めて同じカードは出ない() {
        for (int day = 0; day < 100; day++) {
            DailyQuiz quiz = DailyQuiz.of(FIRST_DAY.plusDays(day), FIRST_DAY, CARDS_WITH_HEALTH);

            assertEquals(DailyQuiz.QUESTIONS_PER_DAY, quiz.questions().size());
            List<Integer> ids = cardIds(quiz);
            assertEquals(ids.size(), new HashSet<>(ids).size());
        }
    }

    @Test
    void 回は第1回の日から数える() {
        assertEquals(1, DailyQuiz.of(FIRST_DAY, FIRST_DAY, CARDS).number());
        assertEquals(9, DailyQuiz.of(LocalDate.of(2026, 11, 3), FIRST_DAY, CARDS).number());
    }

    @Test
    void 選択肢は正解を含む連続した4つで全カードのコストの範囲に収まる() {
        for (int day = 0; day < 100; day++) {
            for (QuizQuestion.Elixir question : elixirs(DailyQuiz.of(FIRST_DAY.plusDays(day), FIRST_DAY,
                    CARDS_WITH_HEALTH))) {
                List<Integer> choices = question.choices();

                assertEquals(4, choices.size());
                assertEquals(costOf(question.cardId()), question.correctChoice());
                assertTrue(choices.get(0) >= 1 && choices.get(3) <= 9);
                for (int i = 1; i < choices.size(); i++) {
                    assertEquals(choices.get(i - 1) + 1, choices.get(i));
                }
            }
        }
    }

    @Test
    void 端のコストは選択肢の位置が決まる() {
        List<DailyQuiz.Card> cards = List.of(new DailyQuiz.Card(1, false, 1), new DailyQuiz.Card(2, false, 9),
                new DailyQuiz.Card(3, false, 5));

        for (QuizQuestion.Elixir question : elixirs(DailyQuiz.of(FIRST_DAY, FIRST_DAY, cards))) {
            switch (costOf(cards, question.cardId())) {
                case 1 -> assertEquals(List.of(1, 2, 3, 4), question.choices());
                case 9 -> assertEquals(List.of(6, 7, 8, 9), question.choices());
                default -> assertTrue(question.choices().contains(5));
            }
        }
    }

    @Test
    void 正解の位置は偏らない() {
        int[] positions = new int[4];
        // 範囲を1〜9にするための2枚と、4エリクサーの38枚。
        List<DailyQuiz.Card> fours = new ArrayList<>(
                List.of(new DailyQuiz.Card(0, false, 1), new DailyQuiz.Card(1, false, 9)));
        IntStream.range(2, 40).forEach(i -> fours.add(new DailyQuiz.Card(i, false, 4)));
        for (int day = 0; day < 200; day++) {
            elixirs(DailyQuiz.of(FIRST_DAY.plusDays(day), FIRST_DAY, fours)).stream()
                    .filter(question -> question.correctChoice() == 4)
                    .forEach(question -> positions[question.answer()]++);
        }

        // 4エリクサーは1〜4から4〜7の4通りの組があり、それぞれ1/4ずつになるはず(約5,600問)。
        int total = IntStream.of(positions).sum();
        for (int count : positions) {
            assertTrue(Math.abs(count - total / 4.0) < total * 0.03, () -> "偏り: " + Arrays.toString(positions));
        }
    }

    @Test
    void カードが30枚に満たなければあるだけ出す() {
        List<DailyQuiz.Card> cards = List.of(new DailyQuiz.Card(1, false, 3), new DailyQuiz.Card(2, false, 4));

        assertEquals(2, DailyQuiz.of(FIRST_DAY, FIRST_DAY, cards).questions().size());
    }

    @Test
    void コストの幅が4つに満たなければあるだけを選択肢にする() {
        List<DailyQuiz.Card> cards = List.of(new DailyQuiz.Card(1, false, 3), new DailyQuiz.Card(2, false, 4));

        for (QuizQuestion.Elixir question : elixirs(DailyQuiz.of(FIRST_DAY, FIRST_DAY, cards))) {
            assertEquals(List.of(3, 4), question.choices());
            assertEquals(costOf(cards, question.cardId()), question.correctChoice());
        }
    }

    @Test
    void 並べ替えは10問ごとに2問ずつ混ざり何問目に出るかは日ごとに変わる() {
        Set<List<Integer>> patterns = new HashSet<>();
        for (int day = 0; day < 100; day++) {
            List<QuizQuestion> questions = DailyQuiz.of(FIRST_DAY.plusDays(day), FIRST_DAY, CARDS_WITH_HEALTH)
                    .questions();

            List<Integer> slots = IntStream.range(0, questions.size())
                    .filter(i -> questions.get(i) instanceof QuizQuestion.HpOrder)
                    .boxed()
                    .toList();
            for (int start = 0; start < DailyQuiz.QUESTIONS_PER_DAY; start += 10) {
                int from = start;
                assertEquals(2, slots.stream().filter(slot -> slot >= from && slot < from + 10).count());
            }
            patterns.add(slots);
        }
        assertTrue(patterns.size() > 90, () -> "並べ替えの位置の組み合わせが少ない: " + patterns.size());
    }

    @Test
    void 並べ替えの4枚は並べたときに隣り合うヒットポイントが10パーセント以上離れている() {
        for (int day = 0; day < 200; day++) {
            for (QuizQuestion.HpOrder question : hpOrders(DailyQuiz.of(FIRST_DAY.plusDays(day), FIRST_DAY,
                    CARDS_WITH_HEALTH))) {
                List<Integer> healths = question.answer().stream().map(i -> question.cards().get(i).health()).toList();

                assertEquals(4, healths.size());
                for (int i = 1; i < healths.size(); i++) {
                    assertTrue(healths.get(i - 1) * 100 >= healths.get(i) * 110, () -> "近すぎる: " + healths);
                }
            }
        }
    }

    @Test
    void 並べ替えの正解はヒットポイントの多い順で最初に見せる並びとは違う() {
        for (int day = 0; day < 200; day++) {
            for (QuizQuestion.HpOrder question : hpOrders(DailyQuiz.of(FIRST_DAY.plusDays(day), FIRST_DAY,
                    CARDS_WITH_HEALTH))) {
                List<Integer> answer = question.answer();

                assertEquals(Set.of(0, 1, 2, 3), new HashSet<>(answer));
                assertNotEquals(List.of(0, 1, 2, 3), answer);
                for (int i = 1; i < answer.size(); i++) {
                    assertTrue(question.cards().get(answer.get(i - 1)).health()
                            > question.cards().get(answer.get(i)).health());
                }
            }
        }
    }

    @Test
    void 並べ替えの正解はヒットポイントの多い順の位置の並び() {
        QuizQuestion.HpOrder question = new QuizQuestion.HpOrder(List.of(new QuizQuestion.HpCard(1, null, 500),
                new QuizQuestion.HpCard(2, null, 3000), new QuizQuestion.HpCard(3, null, 130), new QuizQuestion.HpCard(4, null, 1200)));

        assertEquals(List.of(1, 3, 0, 2), question.answer());
    }

    @Test
    void ヒットポイントのあるカードが無ければエリクサーの問題だけにする() {
        DailyQuiz quiz = DailyQuiz.of(FIRST_DAY, FIRST_DAY, CARDS);

        assertEquals(DailyQuiz.QUESTIONS_PER_DAY, quiz.questions().size());
        quiz.questions().forEach(question -> assertInstanceOf(QuizQuestion.Elixir.class, question));
    }

    @Test
    void ヒットポイントが近いカードしか無ければその位置はエリクサーの問題にする() {
        // 1000〜1039の40枚は、どの4枚を選んでも隣どうしが10%離れない。
        List<DailyQuiz.Card> cards = IntStream.range(0, 80)
                .mapToObj(i -> new DailyQuiz.Card(i, false, cost(i), i < 40 ? 1000 + i : null))
                .toList();

        DailyQuiz quiz = DailyQuiz.of(FIRST_DAY, FIRST_DAY, cards);

        assertEquals(DailyQuiz.QUESTIONS_PER_DAY, quiz.questions().size());
        quiz.questions().forEach(question -> assertInstanceOf(QuizQuestion.Elixir.class, question));
    }

    @Test
    void カードとは別のユニットも並べ替えに出て渡す順に関係なく同じ問題になる() {
        // 元のカード(26000000)とは別のヒットポイントを持つユニット2つ(モンスターの役と、カードにヒットポイントが無いカードの役)
        List<DailyQuiz.Unit> units = List.of(new DailyQuiz.Unit(26_000_000, "monster", 9000),
                new DailyQuiz.Unit(26_000_100, "boy", 50));
        List<DailyQuiz.Unit> reversed = new ArrayList<>(units);
        Collections.reverse(reversed);

        Set<String> seen = new HashSet<>();
        for (int day = 0; day < 200; day++) {
            LocalDate date = FIRST_DAY.plusDays(day);
            DailyQuiz quiz = DailyQuiz.of(date, FIRST_DAY, CARDS_WITH_HEALTH, units);
            assertEquals(quiz, DailyQuiz.of(date, FIRST_DAY, CARDS_WITH_HEALTH, reversed));
            hpOrders(quiz).stream()
                    .flatMap(question -> question.cards().stream())
                    .filter(card -> card.unit() != null)
                    .forEach(card -> {
                        assertEquals(card.unit().equals("monster") ? 9000 : 50, card.health());
                        seen.add(card.unit());
                    });
        }
        assertEquals(Set.of("monster", "boy"), seen);
    }

    @Test
    void ユニットだけが並べ替えに出た日も元のカードはエリクサーの問題に出せる() {
        // カードは30枚ちょうどなので、ユニットの元のカード(0〜3)を除くとエリクサーの29問に足りなくなる
        List<DailyQuiz.Card> cards = IntStream.range(0, 30)
                .mapToObj(i -> new DailyQuiz.Card(i, false, cost(i)))
                .toList();
        List<DailyQuiz.Unit> units = List.of(new DailyQuiz.Unit(0, "a", 100), new DailyQuiz.Unit(1, "b", 200),
                new DailyQuiz.Unit(2, "c", 400), new DailyQuiz.Unit(3, "d", 800));

        DailyQuiz quiz = DailyQuiz.of(FIRST_DAY, FIRST_DAY, cards, units);

        assertEquals(1, hpOrders(quiz).size());
        assertEquals(DailyQuiz.QUESTIONS_PER_DAY - 1, elixirs(quiz).size());
    }

    private static List<QuizQuestion.Elixir> elixirs(DailyQuiz quiz) {
        return quiz.questions().stream()
                .filter(QuizQuestion.Elixir.class::isInstance)
                .map(QuizQuestion.Elixir.class::cast)
                .toList();
    }

    private static List<QuizQuestion.HpOrder> hpOrders(DailyQuiz quiz) {
        return quiz.questions().stream()
                .filter(QuizQuestion.HpOrder.class::isInstance)
                .map(QuizQuestion.HpOrder.class::cast)
                .toList();
    }

    /** エリクサーの問題のカードと、並べ替えの4枚。 */
    private static List<Integer> cardIds(DailyQuiz quiz) {
        return quiz.questions().stream()
                .flatMap(question -> switch (question) {
                    case QuizQuestion.Elixir elixir -> Stream.of(elixir.cardId());
                    case QuizQuestion.HpOrder hpOrder -> hpOrder.cards().stream().map(QuizQuestion.HpCard::id);
                })
                .toList();
    }

    private static int costOf(int cardId) {
        return costOf(CARDS, cardId);
    }

    private static int costOf(List<DailyQuiz.Card> cards, int cardId) {
        return cards.stream().filter(card -> card.id() == cardId).findFirst().orElseThrow().elixirCost();
    }
}
