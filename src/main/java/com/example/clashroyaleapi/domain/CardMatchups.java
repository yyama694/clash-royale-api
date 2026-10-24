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
import java.util.function.IntPredicate;
import java.util.function.ToDoubleFunction;

/**
 * 世界上位どうしのランク戦({@link TopBattle})から、「このカード入りのデッキが、どのカード入りのデッキに勝ちやすく
 * (負けやすく)かったか」を求める(カード詳細画面の「〇〇入りのデッキが得意なカード」「〇〇入りのデッキが苦手なカード」)。
 *
 * カード1枚の強さではなく、そのカードが入ったデッキ全体の相性である点に注意(画面にも書く)。
 * 両方のデッキに入っていたカードは、その対戦では差にならないので数えない。引き分けも数えない。
 */
public final class CardMatchups {

    /** これ未満の対戦数の組み合わせは出さない。 */
    public static final int MIN_BATTLES = 100;
    static final int LIST_LIMIT = 5;

    private final int battles;
    private final Instant from;
    private final Instant to;
    private final Map<Integer, Stats> byCard;

    private CardMatchups(int battles, Instant from, Instant to, Map<Integer, Stats> byCard) {
        this.battles = battles;
        this.from = from;
        this.to = to;
        this.byCard = byCard;
    }

    /**
     * あるカードが片方のデッキだけに入っていた対戦の、そのカード入りのデッキの側から見た集計。
     *
     * @param battles     そのような対戦の数
     * @param wins        そのうち、そのカード入りのデッキが勝った数
     * @param favorable   相手のデッキにだけ入っていて、そのカード入りのデッキの勝率が全体({@code wins / battles})より
     *                    高かったカード(得意なカード)。勝率の高い方から、対戦数を加味した順(Wilson score の下限の高い順)
     * @param unfavorable 同じく、勝率が全体より低かったカード(苦手なカード)。勝率の低い方から、対戦数を加味した順
     */
    public record Stats(int battles, int wins, List<Opponent> favorable, List<Opponent> unfavorable) {

        public int winRatePercent() {
            return percent(wins, battles);
        }
    }

    /** wins は、相手のデッキにそのカードが入っていた対戦のうち、集計の対象のカード入りのデッキが勝った数。 */
    public record Opponent(int cardId, int battles, int wins) {

        public int winRatePercent() {
            return percent(wins, battles);
        }
    }

    private static int percent(int wins, int battles) {
        return Math.round(100f * wins / battles);
    }

    public static CardMatchups of(Collection<TopBattle> topBattles) {
        // versus[x][y] = x入り(yは無し)のデッキと、y入り(xは無し)のデッキが当たった対戦の [数, x側が勝った数]
        Map<Integer, Map<Integer, int[]>> versus = new HashMap<>();
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
            tally(versus, totals, winner.get(), loser, true);
            tally(versus, totals, loser, winner.get(), false);
        }
        Map<Integer, Stats> byCard = new HashMap<>();
        totals.forEach((card, total) -> {
            Map<Integer, int[]> byOpponent = versus.getOrDefault(card, Map.of());
            int baseline = percent(total[1], total[0]);
            // 全体の勝率より高い(低い)ものだけ(プレイヤー情報画面の得意/苦手カードと同じ考え方)。画面に出す整数の勝率で比べる
            // (わずかに上回るだけだと、全体と同じ勝率が「得意なカード」に並んでしまうため。2026-10-18にユーザーが決定)。
            List<Opponent> favorable = opponents(byOpponent, rate -> rate > baseline,
                    o -> WilsonScore.lowerBound(o.wins(), o.battles()));
            List<Opponent> unfavorable = opponents(byOpponent, rate -> rate < baseline,
                    o -> WilsonScore.lowerBound(o.battles() - o.wins(), o.battles()));
            byCard.put(card, new Stats(total[0], total[1], favorable, unfavorable));
        });
        return new CardMatchups(decided, from, to, byCard);
    }

    /** side から見た1戦。side のデッキにだけあるカード x と、相手のデッキにだけあるカード y の組を、x の側から数える。 */
    private static void tally(Map<Integer, Map<Integer, int[]>> versus, Map<Integer, int[]> totals,
            TopBattle.Side side, TopBattle.Side opponent, boolean won) {
        Set<Integer> mine = new HashSet<>(side.cardIds());
        Set<Integer> theirs = new HashSet<>(opponent.cardIds());
        for (int x : mine) {
            if (theirs.contains(x)) {
                continue;
            }
            int[] total = totals.computeIfAbsent(x, k -> new int[2]);
            total[0]++;
            if (won) {
                total[1]++;
            }
            Map<Integer, int[]> byOpponent = versus.computeIfAbsent(x, k -> new HashMap<>());
            for (int y : theirs) {
                if (mine.contains(y)) {
                    continue;
                }
                int[] pair = byOpponent.computeIfAbsent(y, k -> new int[2]);
                pair[0]++;
                if (won) {
                    pair[1]++;
                }
            }
        }
    }

    /** strength は、得意(苦手)である確からしさ。高い順に並べる。 */
    private static List<Opponent> opponents(Map<Integer, int[]> byOpponent, IntPredicate rateFilter,
            ToDoubleFunction<Opponent> strength) {
        return byOpponent.entrySet().stream()
                .filter(entry -> entry.getValue()[0] >= MIN_BATTLES)
                .map(entry -> new Opponent(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .filter(opponent -> rateFilter.test(opponent.winRatePercent()))
                .sorted(Comparator.comparingDouble(strength).reversed()
                        .thenComparing(Comparator.comparingInt(Opponent::battles).reversed())
                        .thenComparingInt(Opponent::cardId))
                .limit(LIST_LIMIT)
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

    /** そのカードが片方のデッキだけに入っていた対戦が無ければ空。 */
    public Optional<Stats> decksWith(int cardId) {
        return Optional.ofNullable(byCard.get(cardId));
    }
}
