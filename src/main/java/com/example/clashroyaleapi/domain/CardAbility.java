package com.example.clashroyaleapi.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * ヒーローとチャンピオンのスキル(cards/abilities.tsv)。発動に払うエリクサーは公式APIにもステータスの表にも無いので、
 * ゲームのカード情報の画面から読み取った(card-stats-capture スキル)。スキル名は文言の ability.<英語名>。
 *
 * @param card     カードの英語名(公式API)
 * @param champion チャンピオンのスキルなら true、ヒーローのスキルなら false
 * @param cost     発動に払うエリクサー
 */
public record CardAbility(String card, boolean champion, int cost) {

    public static List<CardAbility> parse(List<String> lines) {
        List<CardAbility> abilities = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split("\t", -1);
            if (f.length != 3) {
                throw new IllegalArgumentException("card ability row needs 3 columns: " + line);
            }
            boolean champion = switch (f[1]) {
                case "champion" -> true;
                case "hero" -> false;
                default -> throw new IllegalArgumentException("unknown ability form: " + line);
            };
            abilities.add(new CardAbility(f[0], champion, Integer.parseInt(f[2])));
        }
        return List.copyOf(abilities);
    }
}
