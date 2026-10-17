package com.example.clashroyaleapi.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 世界上位どうしのランク戦({@link TopBattle})から、「このカード入りのデッキと当たったとき、勝率が高かったデッキに
 * 入っていたカード」を求める(カード詳細画面の「〇〇入りのデッキに勝っているカード」)。
 *
 * カード1枚の強さではなく、そのカードが入ったデッキ全体の相性である点に注意(画面にも書く)。
 * 両方のデッキに入っていたカードは、その対戦では差にならないので数えない。引き分けも数えない。
 */
public final class CardMatchups {

    /** これ未満の対戦数の組み合わせは出さない。 */
    public static final int MIN_BATTLES = 100;
    static final int COUNTER_LIMIT = 5;

    private final int battles;
    private final Instant from;
    private final Instant to;
    private final Map<Integer, Against> byCard;

    private CardMatchups(int battles, Instant from, Instant to, Map<Integer, Against> byCard) {
        this.battles = battles;
        this.from = from;
        this.to = to;
        this.byCard = byCard;
    }

    /**
     * あるカードが片方のデッキだけに入っていた対戦の集計。
     *
     * @param battles  そのような対戦の数
     * @param wins     そのうち、そのカードを持たない側が勝った数
     * @param counters そのカードを持たない側のデッキに入っていて、勝率が全体({@code wins / battles})より高かったカード。
     *                 対戦数を加味した順(Wilson score の下限の高い順)
     */
    public record Against(int battles, int wins, List<Counter> counters) {

        public int winRatePercent() {
            return Math.round(100f * wins / battles);
        }
    }

    public record Counter(int cardId, int battles, int wins) {

        public int winRatePercent() {
            return Math.round(100f * wins / battles);
        }
    }

    public static CardMatchups of(Collection<TopBattle> topBattles) {
        // against[y][x] = y入りのデッキと、x入り(yは無し)のデッキが当たった対戦の [数, x側が勝った数]
        Map<Integer, Map<Integer, int[]>> against = new HashMap<>();
        Map<Integer, int[]> totals = new HashMap<>();
        int decided = 0;
        Instant from = null;
        Instant to = null;
        for (TopBattle battle : topBattles) {
            Optional<TopBattle.Side> winner = battle.winner();
            if (winner.isEmpty()) {
                continue;
            }
            decided++;
            from = from == null || battle.time().isBefore(from) ? battle.time() : from;
            to = to == null || battle.time().isAfter(to) ? battle.time() : to;
            TopBattle.Side loser = battle.loser().orElseThrow();
            tally(against, totals, winner.get(), loser, true);
            tally(against, totals, loser, winner.get(), false);
        }
        Map<Integer, Against> byCard = new HashMap<>();
        totals.forEach((card, total) -> byCard.put(card,
                new Against(total[0], total[1], countersOf(total, against.getOrDefault(card, Map.of())))));
        return new CardMatchups(decided, from, to, byCard);
    }

    /** side から見た1戦。side のデッキにだけあるカード x と、相手のデッキにだけあるカード y の組を数える。 */
    private static void tally(Map<Integer, Map<Integer, int[]>> against, Map<Integer, int[]> totals,
            TopBattle.Side side, TopBattle.Side opponent, boolean won) {
        Set<Integer> mine = new HashSet<>(side.cardIds());
        Set<Integer> theirs = new HashSet<>(opponent.cardIds());
        for (int y : theirs) {
            if (mine.contains(y)) {
                continue;
            }
            int[] total = totals.computeIfAbsent(y, k -> new int[2]);
            total[0]++;
            if (won) {
                total[1]++;
            }
            Map<Integer, int[]> byCounter = against.computeIfAbsent(y, k -> new HashMap<>());
            for (int x : mine) {
                if (theirs.contains(x)) {
                    continue;
                }
                int[] pair = byCounter.computeIfAbsent(x, k -> new int[2]);
                pair[0]++;
                if (won) {
                    pair[1]++;
                }
            }
        }
    }

    private static List<Counter> countersOf(int[] total, Map<Integer, int[]> byCounter) {
        return byCounter.entrySet().stream()
                .filter(entry -> entry.getValue()[0] >= MIN_BATTLES)
                // 全体の勝率より高いものだけ(プレイヤー情報画面の得意カードと同じ考え方)。
                .filter(entry -> (long) entry.getValue()[1] * total[0] > (long) total[1] * entry.getValue()[0])
                .map(entry -> new Counter(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .sorted(Comparator.comparingDouble((Counter c) -> WilsonScore.lowerBound(c.wins(), c.battles()))
                        .reversed()
                        .thenComparing(Comparator.comparingInt(Counter::battles).reversed())
                        .thenComparingInt(Counter::cardId))
                .limit(COUNTER_LIMIT)
                .toList();
    }

    /** 数えた対戦(引き分けを除く)の数。 */
    public int battles() {
        return battles;
    }

    /** 数えた対戦のうち最も古い対戦の時刻。対戦が無ければ null。 */
    public Instant from() {
        return from;
    }

    /** 数えた対戦のうち最も新しい対戦の時刻。対戦が無ければ null。 */
    public Instant to() {
        return to;
    }

    /** そのカード入りのデッキと当たった対戦が無ければ空。 */
    public Optional<Against> against(int cardId) {
        return Optional.ofNullable(byCard.get(cardId));
    }
}
