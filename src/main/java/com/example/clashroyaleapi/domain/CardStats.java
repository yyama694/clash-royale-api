package com.example.clashroyaleapi.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // タワーへのダメージと、比べる相手(同じ欄のユニットへのダメージ)。並びは探す順
    private static final Map<String, List<String>> TOWER_DAMAGE_SOURCES = Map.of(
            "stat.TID_SPELL_ATTRIBUTE_TOWER_DAMAGE", List.of("stat.TID_SPELL_ATTRIBUTE_DAMAGE",
                    "stat.TID_SPELL_ATTRIBUTE_AREA_DAMAGE", "stat.TID_SPELL_ATTRIBUTE_RANGED_DAMAGE"),
            "stat.TID_SPELL_ATTRIBUTE_TOWER_DPS", List.of("stat.TID_SPELL_ATTRIBUTE_DAMAGE_PER_SECOND",
                    "stat.TID_POISON_DPS"),
            "stat.TID_TYPE_REFLECTED_TOWER_DAMAGE", List.of("stat.TID_TYPE_REFLECTED_DAMAGE"));
    private static final Pattern WHOLE_NUMBER = Pattern.compile("(\\d+)( x\\d+)?");

    /**
     * タワーへのダメージが、同じ欄のユニットへのダメージから何％減っているか(レベル16の値で計算し、整数に丸める)。
     * 割合は公式の数値ではなく、画面の2つの値から求めたもの。限界突破の欄は通常と同じ値を載せないので、
     * 比べる相手が無ければ通常の欄から探す。範囲の値("24-122")や相手が見つからないときは empty。
     */
    public static OptionalInt towerDamageReduction(Row tower, List<Row> cardRows) {
        List<String> sources = TOWER_DAMAGE_SOURCES.get(tower.labelKey());
        if (sources == null) {
            return OptionalInt.empty();
        }
        Row source = findSource(sources, tower.form(), cardRows);
        if (source == null && tower.form() == Form.EVOLUTION) {
            source = findSource(sources, Form.NORMAL, cardRows);
        }
        if (source == null) {
            return OptionalInt.empty();
        }
        Matcher t = WHOLE_NUMBER.matcher(tower.level16());
        Matcher s = WHOLE_NUMBER.matcher(source.level16());
        if (!t.matches() || !s.matches() || !Objects.equals(t.group(2), s.group(2))) {
            return OptionalInt.empty();
        }
        int towerDamage = Integer.parseInt(t.group(1));
        int damage = Integer.parseInt(s.group(1));
        if (damage == 0 || towerDamage >= damage) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(100 - (int) Math.round(100.0 * towerDamage / damage));
    }

    private static Row findSource(List<String> sources, Form form, List<Row> cardRows) {
        for (String key : sources) {
            for (Row row : cardRows) {
                if (row.form() == form && row.labelKey().equals(key)) {
                    return row;
                }
            }
        }
        return null;
    }

    /** 載っていないカード(タワーユニットなど)は空。 */
    public List<Row> of(String card) {
        return byCard.getOrDefault(card, List.of());
    }

    /** ゲームの画面の「エリクサー（地上）」。地上に出すと別のコストになるカード(スピリットエンプレス)にだけある。 */
    public static final String GROUND_ELIXIR = "stat.TID_SPELL_ATTRIBUTE_ALTERNATE_CARD";

    /** 地上に出したときのエリクサー。地上に出せる別の形が無いカードは empty。 */
    public OptionalInt groundElixir(String card) {
        return of(card).stream()
                .filter(row -> row.labelKey().equals(GROUND_ELIXIR))
                .mapToInt(row -> Integer.parseInt(row.level16()))
                .findFirst();
    }

    public static final String HEALTH = "stat.TID_SPELL_ATTRIBUTE_HEALTH";

    /** 同時出撃数。これがあるカードは何体も出て、ヒットポイントは1体あたり。 */
    public static final String COUNT = "stat.TID_SPELL_ATTRIBUTE_COUNT";

    /** シールドのヒットポイント。これがあるカードのヒットポイントはシールドを含まない。 */
    public static final String SHIELD_HEALTH = "stat.TID_SPELL_ATTRIBUTE_SHIELD_HEALTH";

    /** 通常の形にその項目があるか。 */
    public boolean has(String card, String labelKey) {
        return of(card).stream().anyMatch(row -> row.form() == Form.NORMAL && row.labelKey().equals(labelKey));
    }

    /** 通常の形のレベル16のヒットポイント。呪文など、ヒットポイントが無い(整数で書かれていない)カードは empty。 */
    public OptionalInt health(String card) {
        return of(card).stream()
                .filter(row -> row.form() == Form.NORMAL && row.labelKey().equals(HEALTH))
                .map(Row::level16)
                .filter(value -> value.matches("\\d+"))
                .mapToInt(Integer::parseInt)
                .findFirst();
    }

    /**
     * 地上に出したときの形の英語名。ゲームはこれを別のカードとして持っている(公式の表記は "Spirit Empress (Ground)"・
     * 「スピリットエンプレス（地上）」)。公式APIのカード一覧には無いので、名前の訳とステータスはこの名前で引く。
     */
    public static String groundCard(String card) {
        return card + " (Ground)";
    }
}
