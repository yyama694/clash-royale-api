package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyQuizTest {

    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 10, 26);

    // 2026-10の実際の分布に近い122枚(1〜9エリクサー)。
    private static final List<DailyQuiz.Card> CARDS = IntStream.range(0, 122)
            .mapToObj(i -> new DailyQuiz.Card(26_000_000 + i, false, cost(i)))
            .toList();

    private static int cost(int i) {
        int[] costs = {1, 2, 2, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 6, 6, 7, 8, 9};
        return costs[i % costs.length];
    }

    @Test
    void 同じ日付なら同じ問題になる() {
        LocalDate date = LocalDate.of(2026, 11, 3);

        assertEquals(DailyQuiz.of(date, FIRST_DAY, CARDS), DailyQuiz.of(date, FIRST_DAY, CARDS));
    }

    @Test
    void 公式APIの並び順が変わっても同じ問題になる() {
        LocalDate date = LocalDate.of(2026, 11, 3);
        List<DailyQuiz.Card> shuffled = new ArrayList<>(CARDS);
        Collections.reverse(shuffled);

        assertEquals(DailyQuiz.of(date, FIRST_DAY, CARDS), DailyQuiz.of(date, FIRST_DAY, shuffled));
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
            for (QuizQuestion question : quiz.questions()) {
                if (question.cardId() == 28_000_025) {
                    assertEquals(question.ground() ? 3 : 6, question.correctChoice());
                }
            }
            bothSeen |= quiz.questions().stream().filter(q -> q.cardId() == 28_000_025).count() == 2;
        }
        // 2つの形は別のカードなので、同じ日に両方出ることもある
        assertTrue(bothSeen);
    }

    @Test
    void 日付が違えば問題が変わる() {
        DailyQuiz today = DailyQuiz.of(LocalDate.of(2026, 11, 3), FIRST_DAY, CARDS);
        DailyQuiz tomorrow = DailyQuiz.of(LocalDate.of(2026, 11, 4), FIRST_DAY, CARDS);

        assertNotEquals(cardIds(today), cardIds(tomorrow));
    }

    @Test
    void 一日の30問に同じカードは出ない() {
        for (int day = 0; day < 100; day++) {
            DailyQuiz quiz = DailyQuiz.of(FIRST_DAY.plusDays(day), FIRST_DAY, CARDS);

            assertEquals(DailyQuiz.QUESTIONS_PER_DAY, quiz.questions().size());
            assertEquals(DailyQuiz.QUESTIONS_PER_DAY, new HashSet<>(cardIds(quiz)).size());
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
            for (QuizQuestion question : DailyQuiz.of(FIRST_DAY.plusDays(day), FIRST_DAY, CARDS).questions()) {
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

        for (QuizQuestion question : DailyQuiz.of(FIRST_DAY, FIRST_DAY, cards).questions()) {
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
            DailyQuiz.of(FIRST_DAY.plusDays(day), FIRST_DAY, fours).questions().stream()
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

        for (QuizQuestion question : DailyQuiz.of(FIRST_DAY, FIRST_DAY, cards).questions()) {
            assertEquals(List.of(3, 4), question.choices());
            assertEquals(costOf(cards, question.cardId()), question.correctChoice());
        }
    }

    private static List<Integer> cardIds(DailyQuiz quiz) {
        return quiz.questions().stream().map(QuizQuestion::cardId).toList();
    }

    private static int costOf(int cardId) {
        return costOf(CARDS, cardId);
    }

    private static int costOf(List<DailyQuiz.Card> cards, int cardId) {
        return cards.stream().filter(card -> card.id() == cardId).findFirst().orElseThrow().elixirCost();
    }
}
