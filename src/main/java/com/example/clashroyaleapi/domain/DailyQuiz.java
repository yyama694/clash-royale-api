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
 * 10問ごとに、エリクサーの4択6問・ヒットポイントの並べ替え2問・「呪文一撃で倒せるのはどれ?」2問を混ぜる
 * (どの問題数でも割合がそろうように。並べ替えや呪文の問題が作れなかった位置はエリクサーの問題にする)。
 * 1日の中で同じカードは出さない(並べ替えと呪文の問題の候補と、エリクサーの問題のカードも重ならない)。
 * 並べ替えと呪文の問題には、カードとは別のユニット(ゴブリンシュタインのモンスターなど)も1枚分として出る。ユニットと元のカードは
 * 別のものとして扱うので、同じ問題に並ぶこともある(スピリットエンプレスの2つの形と同じ考え方)。
 */
public record DailyQuiz(LocalDate date, int number, List<QuizQuestion> questions) {

    public static final int QUESTIONS_PER_DAY = 30;

    /** 選べる問題数。どれも30問の先頭から出すので、30問の中の何問目までかになる。 */
    public static final List<Integer> SIZES = List.of(10, 20, QUESTIONS_PER_DAY);

    static final int CHOICES = 4;

    static final int BLOCK = 10;

    static final int HP_ORDERS_PER_BLOCK = 2;

    static final int SPELLS_PER_BLOCK = 2;

    static final int HP_ORDER_CARDS = 4;

    /**
     * 並べ替えの4枚は、並べたときに隣り合うカードのヒットポイントがこれだけ(%)離れているものにする
     * (近いと当てずっぽうになるため。2026-10-31にユーザーが決定)。でたらめに4枚選ぶと約6割が当てはまる。
     */
    static final int HP_GAP_PERCENT = 10;

    private static final int HP_ORDER_TRIES = 1000;

    /** 呪文の問題の候補の数。倒せるカードと倒せないカードが少なくとも1枚ずつ入る(2026-11-01にユーザーが決定)。 */
    static final int SPELL_CANDIDATES = 4;

    /**
     * 呪文の問題には、ヒットポイントが呪文のダメージのこの範囲(%)の「際どい」カードを必ず1枚入れる
     * (2026-11-01にユーザーが決定。ほかの3枚は自由に選ぶ)。
     */
    static final int NEAR_LOW_PERCENT = 80;
    static final int NEAR_HIGH_PERCENT = 125;

    /**
     * 時間で削る呪文のダメージは「毎秒ダメージ×効果時間」で出すので、端数で答えがずれないよう、
     * ヒットポイントがダメージのこの範囲(%)に入るカードは候補に出さない。
     */
    static final int OVER_TIME_MARGIN_PERCENT = 3;

    private static final int SPELL_TRIES = 1000;

    /**
     * 出題できるカード。エリクサーが決まっていないカード(鏡)は渡さない。
     * 地上に出すと別のコストになるカード(スピリットエンプレス)は、ゲームと同じく地上の形(ground)を別のカードとして渡す。
     * health はヒットポイントの並べ替えと呪文の問題に出すカードだけにある(レベル16の値)。
     * flying は空を飛ぶカード(地上にしか当たらない呪文の問題には出さない)、building は建物(アースクエイクのダメージが違う)。
     */
    public record Card(int id, boolean ground, int elixirCost, Integer health, boolean flying, boolean building) {

        public Card(int id, boolean ground, int elixirCost) {
            this(id, ground, elixirCost, null, false, false);
        }

        public Card(int id, boolean ground, int elixirCost, Integer health) {
            this(id, ground, elixirCost, health, false, false);
        }
    }

    /**
     * カードとは別にヒットポイントの並べ替えと呪文の問題に出すユニット(ゴブリンシュタインのモンスターなど。CardUnit)。
     * id は元のカードで、画像とカード詳細画面へのリンクに使う。エリクサーの問題には出さない。
     */
    public record Unit(int id, String unit, int health, boolean flying) {

        public Unit(int id, String unit, int health) {
            this(id, unit, health, false);
        }
    }

    /**
     * 「呪文一撃で倒せるのはどれ?」に出す呪文(レベル16)。damage はユニットへのダメージ、buildingDamage は建物へのダメージ
     * (アースクエイクだけ違う)。groundOnly は地上にしか当たらない呪文、overTime は時間で削る呪文(効果時間いっぱいの合計)。
     */
    public record Spell(int id, int damage, int buildingDamage, boolean groundOnly, boolean overTime) {
    }

    /** 並べ替えと呪文の問題の候補。 */
    private record Candidate(QuizQuestion.HpCard card, boolean flying, boolean building) {
    }

    /** firstDay は第1回の日。 */
    public static DailyQuiz of(LocalDate date, LocalDate firstDay, List<Card> cards) {
        return of(date, firstDay, cards, List.of(), List.of());
    }

    public static DailyQuiz of(LocalDate date, LocalDate firstDay, List<Card> cards, List<Unit> units) {
        return of(date, firstDay, cards, units, List.of());
    }

    public static DailyQuiz of(LocalDate date, LocalDate firstDay, List<Card> cards, List<Unit> units,
            List<Spell> spells) {
        // 公式APIの並び順が変わっても同じ問題になるよう、IDの順にそろえてから並べ替える。
        List<Card> deck = new ArrayList<>(cards);
        deck.sort(Comparator.comparingInt(Card::id).thenComparing(Card::ground));
        Random random = new Random(seed(date));
        shuffle(deck, deck.size(), random);
        int count = Math.min(QUESTIONS_PER_DAY, deck.size());
        List<Candidate> pool = new ArrayList<>(deck.stream()
                .filter(card -> card.health() != null)
                .map(card -> new Candidate(new QuizQuestion.HpCard(card.id(), null, card.health()), card.flying(),
                        card.building()))
                .toList());
        units.stream()
                .sorted(Comparator.comparingInt(Unit::id).thenComparing(Unit::unit))
                .forEach(unit -> pool.add(new Candidate(new QuizQuestion.HpCard(unit.id(), unit.unit(), unit.health()),
                        unit.flying(), false)));
        Set<QuizQuestion.HpCard> used = new HashSet<>();
        Map<Integer, QuizQuestion> placed = new HashMap<>();
        List<Integer> hpOrderSlots = new ArrayList<>();
        List<Integer> spellSlots = new ArrayList<>();
        chooseSlots(count, random, hpOrderSlots, spellSlots);
        List<QuizQuestion.HpCard> hpPool = pool.stream().map(Candidate::card).toList();
        for (int slot : hpOrderSlots) {
            hpOrder(hpPool, used, random).ifPresent(question -> placed.put(slot, question));
        }
        List<Spell> spellDeck = new ArrayList<>(spells);
        spellDeck.sort(Comparator.comparingInt(Spell::id));
        shuffle(spellDeck, spellDeck.size(), random);
        int nextSpell = 0;
        for (int slot : spellSlots) {
            // 同じ日に同じ呪文が続かないよう、並べ替えた順に使う。問題が作れない呪文は飛ばす。
            for (int tried = 0; tried < spellDeck.size(); tried++) {
                Spell spell = spellDeck.get(nextSpell++ % spellDeck.size());
                Optional<QuizQuestion> question = spellKill(spell, pool, used, random);
                if (question.isPresent()) {
                    placed.put(slot, question.get());
                    break;
                }
            }
        }
        int lowest = deck.stream().mapToInt(Card::elixirCost).min().orElse(0);
        int highest = deck.stream().mapToInt(Card::elixirCost).max().orElse(0);
        // 並べ替えや呪文の問題に出したカードは、エリクサーの問題に出さない(ユニットだけ出たカードは出す)。
        Iterator<Card> rest = deck.stream()
                .filter(card -> card.health() == null
                        || !used.contains(new QuizQuestion.HpCard(card.id(), null, card.health())))
                .toList().iterator();
        List<QuizQuestion> questions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            QuizQuestion question = placed.get(i);
            if (question != null) {
                questions.add(question);
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

    /** 10問ごとに、並べ替えを出す位置を2つと呪文の問題を出す位置を2つ選ぶ。何問目に出るかは日ごとに変わる。 */
    private static void chooseSlots(int count, Random random, List<Integer> hpOrderSlots, List<Integer> spellSlots) {
        for (int start = 0; start < count; start += BLOCK) {
            List<Integer> block = IntStream.range(start, Math.min(count, start + BLOCK)).boxed()
                    .collect(Collectors.toCollection(ArrayList::new));
            int hpOrders = Math.min(HP_ORDERS_PER_BLOCK, block.size());
            int spells = Math.min(SPELLS_PER_BLOCK, block.size() - hpOrders);
            shuffle(block, hpOrders + spells, random);
            hpOrderSlots.addAll(block.subList(0, hpOrders));
            spellSlots.addAll(block.subList(hpOrders, hpOrders + spells));
        }
    }

    /** ヒットポイントが十分に離れた4枚が見つからなければ empty(その位置はエリクサーの問題にする)。 */
    private static Optional<QuizQuestion> hpOrder(List<QuizQuestion.HpCard> pool, Set<QuizQuestion.HpCard> used,
            Random random) {
        List<QuizQuestion.HpCard> candidates = pool.stream()
                .filter(card -> !used.contains(card))
                .collect(Collectors.toCollection(ArrayList::new));
        if (candidates.size() < HP_ORDER_CARDS) {
            return Optional.empty();
        }
        for (int i = 0; i < HP_ORDER_TRIES; i++) {
            shuffle(candidates, HP_ORDER_CARDS, random);
            List<QuizQuestion.HpCard> picked = List.copyOf(candidates.subList(0, HP_ORDER_CARDS));
            if (spreadOut(picked)) {
                used.addAll(picked);
                return Optional.of(new QuizQuestion.HpOrder(shown(picked, random)));
            }
        }
        return Optional.empty();
    }

    private static boolean spreadOut(List<QuizQuestion.HpCard> cards) {
        List<Integer> healths = cards.stream()
                .map(QuizQuestion.HpCard::health)
                .sorted(Comparator.reverseOrder())
                .toList();
        for (int i = 1; i < healths.size(); i++) {
            if ((long) healths.get(i - 1) * 100 < (long) healths.get(i) * (100 + HP_GAP_PERCENT)) {
                return false;
            }
        }
        return true;
    }

    /** 最初に見せる並び。そのまま答えれば正解になる並び(多い順)にはしない。 */
    private static List<QuizQuestion.HpCard> shown(List<QuizQuestion.HpCard> cards, Random random) {
        List<QuizQuestion.HpCard> shown = new ArrayList<>(cards);
        do {
            shuffle(shown, shown.size(), random);
        } while (IntStream.range(1, shown.size()).allMatch(i -> shown.get(i - 1).health() > shown.get(i).health()));
        return List.copyOf(shown);
    }

    /**
     * 呪文の問題。際どいカードを1枚と、ほかの3枚を選び、倒せるカードが1〜3枚になる組を探す。
     * 見つからなければ empty(次の呪文を試し、どれもだめならその位置はエリクサーの問題にする)。
     */
    private static Optional<QuizQuestion> spellKill(Spell spell, List<Candidate> pool, Set<QuizQuestion.HpCard> used,
            Random random) {
        List<Candidate> eligible = pool.stream()
                .filter(candidate -> !used.contains(candidate.card()))
                .filter(candidate -> !(spell.groundOnly() && candidate.flying()))
                .filter(candidate -> !(spell.overTime() && tooClose(spell, candidate)))
                .collect(Collectors.toCollection(ArrayList::new));
        List<Candidate> near = eligible.stream().filter(candidate -> near(spell, candidate)).toList();
        if (near.isEmpty() || eligible.size() < SPELL_CANDIDATES) {
            return Optional.empty();
        }
        for (int i = 0; i < SPELL_TRIES; i++) {
            Candidate first = near.get(random.nextInt(near.size()));
            List<Candidate> others = new ArrayList<>(eligible);
            others.remove(first);
            shuffle(others, SPELL_CANDIDATES - 1, random);
            List<Candidate> picked = new ArrayList<>();
            picked.add(first);
            picked.addAll(others.subList(0, SPELL_CANDIDATES - 1));
            long killed = picked.stream().filter(candidate -> kills(spell, candidate)).count();
            if (killed >= 1 && killed < SPELL_CANDIDATES) {
                shuffle(picked, picked.size(), random);
                picked.forEach(candidate -> used.add(candidate.card()));
                return Optional.of(new QuizQuestion.SpellKill(spell.id(),
                        picked.stream().map(Candidate::card).toList(),
                        picked.stream().map(candidate -> damageTo(spell, candidate)).toList()));
            }
        }
        return Optional.empty();
    }

    private static int damageTo(Spell spell, Candidate candidate) {
        return candidate.building() ? spell.buildingDamage() : spell.damage();
    }

    /** ヒットポイントがダメージと同じなら倒せる。 */
    private static boolean kills(Spell spell, Candidate candidate) {
        return candidate.card().health() <= damageTo(spell, candidate);
    }

    private static boolean near(Spell spell, Candidate candidate) {
        long health = candidate.card().health();
        long damage = damageTo(spell, candidate);
        return health * 100 >= damage * NEAR_LOW_PERCENT && health * 100 <= damage * NEAR_HIGH_PERCENT;
    }

    private static boolean tooClose(Spell spell, Candidate candidate) {
        long health = candidate.card().health();
        long damage = damageTo(spell, candidate);
        return Math.abs(health - damage) * 100 < damage * OVER_TIME_MARGIN_PERCENT;
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
