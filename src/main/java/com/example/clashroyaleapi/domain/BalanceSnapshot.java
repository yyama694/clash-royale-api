package com.example.clashroyaleapi.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * ある時点の、バランス調整の対象カードの使用率(世界トップ層のデッキ)。調整の前・後の比べに使う。
 *
 * @param collectedOn 集計した日(UTC)
 * @param collectedAt 集計した時刻。手で残した数字は分からないので null
 * @param percent     カード(公式APIの英語名)をその形で使っていた割合(%)。分からなければ空
 */
public record BalanceSnapshot(LocalDate collectedOn, Instant collectedAt, int sampleSize,
        BiFunction<String, CardForm, Optional<Double>> percent) {

    /** 集計の履歴から。集計に無いカード(誰も使っていない)は0%。 */
    public static BalanceSnapshot of(CardUsage usage, Map<String, Integer> cardIdsByName) {
        return new BalanceSnapshot(LocalDate.ofInstant(usage.collectedAt(), ZoneOffset.UTC), usage.collectedAt(),
                usage.sampleSize(),
                (card, form) -> Optional.ofNullable(cardIdsByName.get(card)).map(id -> usage.percentOf(id, form)));
    }

    public Optional<Double> percentOf(String card, CardForm form) {
        return percent.apply(card, form);
    }

    /**
     * {@code balance/usage-before.tsv}(集計の履歴より前の調整の分を手で残したもの)を読む。
     * 調整の実施日ごとに返す。書かれていないカードと、進化・ヒーローの形は空(記録が無い)。
     */
    public static Map<LocalDate, BalanceSnapshot> parse(List<String> lines) {
        Map<LocalDate, LocalDate> collectedOn = new LinkedHashMap<>();
        Map<LocalDate, Integer> sampleSize = new HashMap<>();
        Map<LocalDate, Map<String, Double>> percents = new HashMap<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split("\t", -1);
            if (f.length != 5) {
                throw new IllegalArgumentException("malformed balance baseline line: " + line);
            }
            LocalDate update = LocalDate.parse(f[0]);
            collectedOn.put(update, LocalDate.parse(f[1]));
            sampleSize.put(update, Integer.valueOf(f[2]));
            percents.computeIfAbsent(update, d -> new HashMap<>()).put(f[3], Double.valueOf(f[4]));
        }
        Map<LocalDate, BalanceSnapshot> snapshots = new HashMap<>();
        collectedOn.forEach((update, day) -> {
            Map<String, Double> byCard = Map.copyOf(percents.get(update));
            snapshots.put(update, new BalanceSnapshot(day, null, sampleSize.get(update),
                    (card, form) -> form == CardForm.NORMAL ? Optional.ofNullable(byCard.get(card)) : Optional.empty()));
        });
        return Map.copyOf(snapshots);
    }
}
