package com.example.clashroyaleapi.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * カードのステータス(cards/stats.tsv)。公式APIは返さないので、ゲーム内のカード情報の画面から読み取り、
 * レベル16とレベル11の値にしたもの(作り方は card-stats-capture スキル)。
 *
 * @param asOf 画面を読み取った日。この日より後のバランス調整は、読み取り直すまで入っていない
 */
public record CardStats(LocalDate asOf, Map<String, List<Row>> byCard) {

    public enum Form {
        NORMAL, EVOLUTION, HERO, CHAMPION
    }

    /**
     * 値はゲームの表記に近い形の文字列で、言葉と秒だけは表示言語に合わせるための印が入る
     * ({@code {t:メッセージキー}} と {@code {s:1.8}})。
     *
     * @param labelKey 項目名のメッセージキー。{@code =} で始まるものは、その後ろがそのまま項目名(日本語だけの行)
     * @param level11  レベルごとの倍率から求められない項目(インフェルノ系の毎秒ダメージなど)は null
     * @param language この言語の画面にだけ出す行(公式の訳が無い項目を日本語の画面に出すもの)。全言語なら null
     */
    public record Row(Form form, String labelKey, String level16, String level11, String language) {

        public boolean shownIn(String lang) {
            return language == null || language.equals(lang);
        }
    }

    private static final String AS_OF = "@asof";

    /** カードは公式APIの英語名。行の並びはゲームの画面の順のまま。 */
    public static CardStats parse(List<String> lines) {
        LocalDate asOf = null;
        Map<String, List<Row>> byCard = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split("\t", -1);
            if (f[0].equals(AS_OF)) {
                asOf = LocalDate.parse(f[1]);
                continue;
            }
            if (f.length != 6) {
                throw new IllegalArgumentException("card stats row needs 6 columns: " + line);
            }
            byCard.computeIfAbsent(f[0], card -> new ArrayList<>())
                    .add(new Row(Form.valueOf(f[1].toUpperCase()), f[2], f[3], emptyToNull(f[4]), emptyToNull(f[5])));
        }
        if (asOf == null) {
            throw new IllegalArgumentException("card stats need an " + AS_OF + " line");
        }
        return new CardStats(asOf, byCard);
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    /** 載っていないカード(タワーユニットなど)は空。 */
    public List<Row> of(String card) {
        return byCard.getOrDefault(card, List.of());
    }
}
