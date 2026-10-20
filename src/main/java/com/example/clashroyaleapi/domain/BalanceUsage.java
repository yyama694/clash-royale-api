package com.example.clashroyaleapi.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * バランス調整1回分の、対象カードの使用率の推移(調整前 → 1日後・3日後・7日後 → 今)。
 * そのカードが後の調整でまた変わったときは、「今」ではなく次の調整の前の最後の集計で止める
 * (後の調整の影響まで、この調整の結果に見えてしまうため)。
 *
 * @param before 調整前の集計。無ければ null
 * @param latest 最新の集計。まだ無ければ null
 */
public record BalanceUsage(BalanceSnapshot before, BalanceSnapshot latest, Map<Key, Series> series) {

    /** 調整から何日後(UTCの日付)の集計を出すか。 */
    public static final List<Integer> DAYS_AFTER = List.of(1, 3, 7);

    public record Key(String card, CardForm form) {
    }

    public enum PointKind {
        DAYS_AFTER, NOW, BEFORE_NEXT
    }

    /** @param days DAYS_AFTER のときの日数。それ以外は0 */
    public record Point(PointKind kind, int days, double percent) {
    }

    /**
     * そのカードの、この調整の後の次の調整。
     *
     * @param lastBefore 次の調整の前の最後の集計。この調整と次の調整の間に集計が無ければ null
     */
    public record Next(Instant start, BalanceSnapshot lastBefore) {
    }

    /**
     * @param before 調整前の使用率(%)。記録が無ければ null
     * @param after  調整後の使用率(古い順)。最後が今か次の調整の前
     */
    public record Series(Double before, List<Point> after) {

        static final Series EMPTY = new Series(null, List.of());

        public Optional<Point> last() {
            return after.isEmpty() ? Optional.empty() : Optional.of(after.getLast());
        }

        /** 最後の値 - 調整前(ポイント)。画面の数字と合うよう、小数1桁にしてから引く。どちらかが無ければ null。 */
        public Double delta() {
            return before == null || after.isEmpty() ? null : oneDecimal(oneDecimal(after.getLast().percent())
                    - oneDecimal(before));
        }
    }

    /**
     * 種類(弱体化など)ごとのまとめ。調整前と後の両方があるカードだけで数える。
     *
     * @param largest 使用率の変化がいちばん大きかったカード(同じなら記事の順で先のもの)
     */
    public record Digest(int cards, double averageDelta, Key largest, double largestDelta) {
    }

    public Series of(String card, CardForm form) {
        return series.getOrDefault(new Key(card, form), Series.EMPTY);
    }

    public Optional<Digest> digest(BalanceUpdate update, BalanceUpdate.Kind kind) {
        Map<Key, Double> deltas = new LinkedHashMap<>();
        update.byCard().stream()
                .filter(changes -> changes.kind() == kind)
                .forEach(changes -> {
                    Double delta = of(changes.card(), changes.form()).delta();
                    if (delta != null) {
                        deltas.putIfAbsent(new Key(changes.card(), changes.form()), delta);
                    }
                });
        if (deltas.isEmpty()) {
            return Optional.empty();
        }
        double average = deltas.values().stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        Map.Entry<Key, Double> largest = deltas.entrySet().stream()
                .max(Comparator.comparingDouble(entry -> Math.abs(entry.getValue())))
                .orElseThrow();
        // max は同じ値のとき後のものを返すので、記事の順で先のものを選び直す。
        Key first = deltas.entrySet().stream()
                .filter(entry -> Math.abs(entry.getValue()) == Math.abs(largest.getValue()))
                .findFirst().orElseThrow().getKey();
        return Optional.of(new Digest(deltas.size(), oneDecimal(average), first, deltas.get(first)));
    }

    /**
     * @param daysAfter DAYS_AFTER の日数ごとの、調整から何日後かの日(UTC)の最初の集計。無い日は入れない
     * @param nexts     後の調整でまた変わったカード(形は問わない。通常の形の変更は進化・ヒーローにも効くため)
     */
    public static BalanceUsage of(BalanceUpdate update, BalanceSnapshot before, Map<Integer, BalanceSnapshot> daysAfter,
            BalanceSnapshot latest, Map<String, Next> nexts) {
        Instant start = update.date().atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<Key, Series> series = new LinkedHashMap<>();
        for (BalanceUpdate.CardChanges changes : update.byCard()) {
            Key key = new Key(changes.card(), changes.form());
            Next next = nexts.get(changes.card());
            BalanceSnapshot end = next == null ? latest : next.lastBefore();
            if (end != null && (end.collectedAt() == null || !end.collectedAt().isAfter(start))) {
                end = null;
            }
            List<Point> after = new ArrayList<>();
            for (int days : DAYS_AFTER) {
                BalanceSnapshot snapshot = daysAfter.get(days);
                if (snapshot == null || (next != null && !snapshot.collectedAt().isBefore(next.start()))
                        || (end != null && snapshot.collectedAt().equals(end.collectedAt()))) {
                    continue;
                }
                snapshot.percentOf(key.card(), key.form())
                        .ifPresent(percent -> after.add(new Point(PointKind.DAYS_AFTER, days, percent)));
            }
            if (end != null) {
                PointKind kind = next == null ? PointKind.NOW : PointKind.BEFORE_NEXT;
                end.percentOf(key.card(), key.form()).ifPresent(percent -> after.add(new Point(kind, 0, percent)));
            }
            Double beforePercent = before == null ? null : before.percentOf(key.card(), key.form()).orElse(null);
            series.putIfAbsent(key, new Series(beforePercent, List.copyOf(after)));
        }
        return new BalanceUsage(before, latest, Map.copyOf(series));
    }

    static double oneDecimal(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
