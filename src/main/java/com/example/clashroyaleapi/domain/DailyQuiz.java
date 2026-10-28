package com.example.clashroyaleapi.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * クラロワクイズの「今日のクイズ」。日付(UTC)から毎回同じ問題を計算で作るので、どこにも保存しない。
 * 10問・20問・30問は、ここで作る30問の先頭から出す(設計_クイズ.md)。
 * 今の問題の種類はエリクサーだけ。種類を足して作り方を変えるときは、切り替える日付を決めてその日以降だけ変える
 * (同じ日付の問題が1日の途中で変わらないように)。
 */
public record DailyQuiz(LocalDate date, int number, List<QuizQuestion> questions) {

    public static final int QUESTIONS_PER_DAY = 30;

    /** 選べる問題数。どれも30問の先頭から出すので、30問の中の何問目までかになる。 */
    public static final List<Integer> SIZES = List.of(10, 20, QUESTIONS_PER_DAY);

    static final int CHOICES = 4;

    /**
     * 出題できるカード。エリクサーが決まっていないカード(鏡)は渡さない。
     * 地上に出すと別のコストになるカード(スピリットエンプレス)は、ゲームと同じく地上の形(ground)を別のカードとして渡す。
     */
    public record Card(int id, boolean ground, int elixirCost) {
    }

    /** firstDay は第1回の日。 */
    public static DailyQuiz of(LocalDate date, LocalDate firstDay, List<Card> cards) {
        // 公式APIの並び順が変わっても同じ問題になるよう、IDの順にそろえてから並べ替える。
        List<Card> deck = new ArrayList<>(cards);
        deck.sort(Comparator.comparingInt(Card::id).thenComparing(Card::ground));
        Random random = new Random(seed(date));
        int count = Math.min(QUESTIONS_PER_DAY, deck.size());
        // Collections.shuffle は中身が仕様で決まっておらず、Javaの版で並びが変わりうるので、Fisher-Yatesを自分で書く。
        // Random の計算方法はJavadocで決まっている。
        for (int i = 0; i < count; i++) {
            Collections.swap(deck, i, i + random.nextInt(deck.size() - i));
        }
        int lowest = deck.stream().mapToInt(Card::elixirCost).min().orElse(0);
        int highest = deck.stream().mapToInt(Card::elixirCost).max().orElse(0);
        List<QuizQuestion> questions = deck.subList(0, count).stream()
                .map(card -> elixirQuestion(card, lowest, highest, random))
                .toList();
        return new DailyQuiz(date, (int) ChronoUnit.DAYS.between(firstDay, date) + 1, questions);
    }

    /**
     * 選択肢は正解を含む連続した4つの数字で、全カードのコストの範囲からはみ出さない。
     * 当てはまる組のどれにするかを乱数で選び、正解の位置が偏らないようにする(1と9のような端は位置が決まってしまう)。
     */
    private static QuizQuestion elixirQuestion(Card card, int lowest, int highest, Random random) {
        int cost = card.elixirCost();
        int firstStart = Math.max(lowest, cost - (CHOICES - 1));
        int lastStart = Math.min(cost, highest - (CHOICES - 1));
        int start = lastStart < firstStart ? lowest : firstStart + random.nextInt(lastStart - firstStart + 1);
        int end = Math.min(highest, start + CHOICES - 1);
        List<Integer> choices = IntStream.rangeClosed(start, end).boxed().toList();
        return new QuizQuestion(QuizQuestion.Type.ELIXIR, card.id(), card.ground(), choices, cost - start);
    }

    /**
     * 日付をそのまま種にすると、隣り合う日付で Random の最初の値が似る。SplitMix64の混ぜ方で散らしてから渡す。
     * 式を変えると過去と今日の問題が変わるので、変えない。
     */
    private static long seed(LocalDate date) {
        long z = date.toEpochDay() + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
