package com.example.clashroyaleapi.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * クラロワクイズの「今日のクイズ」。日付(UTC)から毎回同じ問題を計算で作るので、どこにも保存しない。
 * 10問・20問・30問は、ここで作る30問の先頭から出す(設計_クイズ.md)。
 * エリクサーの4択に、10問ごとに2問ずつヒットポイントの並べ替えを混ぜる(どの問題数でも割合がそろうように)。
 * 1日の中で同じカードは出さない(並べ替えの4枚とエリクサーの問題のカードも重ならない)。
 */
public record DailyQuiz(LocalDate date, int number, List<QuizQuestion> questions) {

    public static final int QUESTIONS_PER_DAY = 30;

    /** 選べる問題数。どれも30問の先頭から出すので、30問の中の何問目までかになる。 */
    public static final List<Integer> SIZES = List.of(10, 20, QUESTIONS_PER_DAY);

    static final int CHOICES = 4;

    static final int BLOCK = 10;

    static final int HP_ORDERS_PER_BLOCK = 2;

    static final int HP_ORDER_CARDS = 4;

    /**
     * 並べ替えの4枚は、並べたときに隣り合うカードのヒットポイントがこれだけ(%)離れているものにする
     * (近いと当てずっぽうになるため。2026-10-31にユーザーが決定)。でたらめに4枚選ぶと約6割が当てはまる。
     */
    static final int HP_GAP_PERCENT = 10;

    private static final int HP_ORDER_TRIES = 1000;

    /**
     * 出題できるカード。エリクサーが決まっていないカード(鏡)は渡さない。
     * 地上に出すと別のコストになるカード(スピリットエンプレス)は、ゲームと同じく地上の形(ground)を別のカードとして渡す。
     * health はヒットポイントの並べ替えに出すカードだけにある(レベル16の値)。
     */
    public record Card(int id, boolean ground, int elixirCost, Integer health) {

        public Card(int id, boolean ground, int elixirCost) {
            this(id, ground, elixirCost, null);
        }
    }

    /** firstDay は第1回の日。 */
    public static DailyQuiz of(LocalDate date, LocalDate firstDay, List<Card> cards) {
        // 公式APIの並び順が変わっても同じ問題になるよう、IDの順にそろえてから並べ替える。
        List<Card> deck = new ArrayList<>(cards);
        deck.sort(Comparator.comparingInt(Card::id).thenComparing(Card::ground));
        Random random = new Random(seed(date));
        shuffle(deck, deck.size(), random);
        int count = Math.min(QUESTIONS_PER_DAY, deck.size());
        Set<Card> used = new HashSet<>();
        Map<Integer, QuizQuestion> hpOrders = new HashMap<>();
        for (int slot : hpOrderSlots(count, random)) {
            hpOrder(deck, used, random).ifPresent(question -> hpOrders.put(slot, question));
        }
        int lowest = deck.stream().mapToInt(Card::elixirCost).min().orElse(0);
        int highest = deck.stream().mapToInt(Card::elixirCost).max().orElse(0);
        Iterator<Card> rest = deck.stream().filter(card -> !used.contains(card)).toList().iterator();
        List<QuizQuestion> questions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            QuizQuestion hpOrder = hpOrders.get(i);
            if (hpOrder != null) {
                questions.add(hpOrder);
            } else if (rest.hasNext()) {
                questions.add(elixirQuestion(rest.next(), lowest, highest, random));
            }
        }
        return new DailyQuiz(date, (int) ChronoUnit.DAYS.between(firstDay, date) + 1, List.copyOf(questions));
    }

    /**
     * Collections.shuffle は中身が仕様で決まっておらず、Javaの版で並びが変わりうるので、Fisher-Yatesを自分で書く。
     * Random の計算方法はJavadocで決まっている。先頭の count 個だけを決める。
     */
    private static <T> void shuffle(List<T> list, int count, Random random) {
        for (int i = 0; i < count; i++) {
            Collections.swap(list, i, i + random.nextInt(list.size() - i));
        }
    }

    /** 10問ごとに、並べ替えを出す位置を2つ選ぶ。何問目に出るかは日ごとに変わる。 */
    private static List<Integer> hpOrderSlots(int count, Random random) {
        List<Integer> slots = new ArrayList<>();
        for (int start = 0; start < count; start += BLOCK) {
            List<Integer> block = IntStream.range(start, Math.min(count, start + BLOCK)).boxed()
                    .collect(Collectors.toCollection(ArrayList::new));
            int take = Math.min(HP_ORDERS_PER_BLOCK, block.size());
            shuffle(block, take, random);
            slots.addAll(block.subList(0, take));
        }
        return slots;
    }

    /** ヒットポイントが十分に離れた4枚が見つからなければ empty(その位置はエリクサーの問題にする)。 */
    private static Optional<QuizQuestion> hpOrder(List<Card> deck, Set<Card> used, Random random) {
        List<Card> candidates = deck.stream()
                .filter(card -> card.health() != null && !used.contains(card))
                .collect(Collectors.toCollection(ArrayList::new));
        if (candidates.size() < HP_ORDER_CARDS) {
            return Optional.empty();
        }
        for (int i = 0; i < HP_ORDER_TRIES; i++) {
            shuffle(candidates, HP_ORDER_CARDS, random);
            List<Card> picked = List.copyOf(candidates.subList(0, HP_ORDER_CARDS));
            if (spreadOut(picked)) {
                used.addAll(picked);
                return Optional.of(new QuizQuestion.HpOrder(shown(picked, random)));
            }
        }
        return Optional.empty();
    }

    private static boolean spreadOut(List<Card> cards) {
        List<Integer> healths = cards.stream().map(Card::health).sorted(Comparator.reverseOrder()).toList();
        for (int i = 1; i < healths.size(); i++) {
            if ((long) healths.get(i - 1) * 100 < (long) healths.get(i) * (100 + HP_GAP_PERCENT)) {
                return false;
            }
        }
        return true;
    }

    /** 最初に見せる並び。そのまま答えれば正解になる並び(多い順)にはしない。 */
    private static List<QuizQuestion.HpCard> shown(List<Card> cards, Random random) {
        List<QuizQuestion.HpCard> shown = cards.stream()
                .map(card -> new QuizQuestion.HpCard(card.id(), card.health()))
                .collect(Collectors.toCollection(ArrayList::new));
        do {
            shuffle(shown, shown.size(), random);
        } while (IntStream.range(1, shown.size()).allMatch(i -> shown.get(i - 1).health() > shown.get(i).health()));
        return List.copyOf(shown);
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
        return new QuizQuestion.Elixir(card.id(), card.ground(), choices, cost - start);
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
