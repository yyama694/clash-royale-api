package com.example.clashroyaleapi.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * カードとは別にヒットポイントを持つユニット(cards/units.tsv)。ゴブリンシュタインのモンスター、ゴーレムの小型ゴーレムなど、
 * そのカードにしかいないユニットだけ。クイズのヒットポイントの並べ替えに、カードとは別のものとして出す。
 *
 * @param card   カードの英語名(公式API)
 * @param unit   どのユニットか(注釈の文言のキーに使う)
 * @param health レベル16のヒットポイント
 */
public record CardUnit(String card, String unit, int health) {

    public static List<CardUnit> parse(List<String> lines) {
        List<CardUnit> units = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split("\t", -1);
            if (f.length != 3) {
                throw new IllegalArgumentException("card unit row needs 3 columns: " + line);
            }
            units.add(new CardUnit(f[0], f[1], Integer.parseInt(f[2])));
        }
        return List.copyOf(units);
    }
}
