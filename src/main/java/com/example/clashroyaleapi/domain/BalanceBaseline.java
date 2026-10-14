package com.example.clashroyaleapi.domain;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * バランス調整の前の使用率(世界トップ層のデッキ)。
 *
 * @param collectedOn 集計した日(UTC)
 * @param percent     カード(公式APIの英語名)の使用率(%)。分からないカードは空
 */
public record BalanceBaseline(LocalDate collectedOn, int sampleSize, Function<String, Optional<Double>> percent) {

    /** 集計の履歴から。集計に無いカード(誰も使っていない)は0%。 */
    public static BalanceBaseline of(CardUsage usage, Map<String, Integer> cardIdsByName) {
        return new BalanceBaseline(LocalDate.ofInstant(usage.collectedAt(), ZoneOffset.UTC),
                usage.sampleSize(),
                card -> Optional.ofNullable(cardIdsByName.get(card)).map(id -> usage.usageOf(id).percent()));
    }

    /**
     * {@code balance/usage-before.tsv}(集計の履歴より前の調整の分を手で残したもの)を読む。
     * 調整の実施日ごとに返す。書かれていないカードは空(記録が無い)。
     */
    public static Map<LocalDate, BalanceBaseline> parse(List<String> lines) {
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
        Map<LocalDate, BalanceBaseline> baselines = new HashMap<>();
        collectedOn.forEach((update, day) -> {
            Map<String, Double> byCard = Map.copyOf(percents.get(update));
            baselines.put(update, new BalanceBaseline(day, sampleSize.get(update),
                    card -> Optional.ofNullable(byCard.get(card))));
        });
        return Map.copyOf(baselines);
    }
}
