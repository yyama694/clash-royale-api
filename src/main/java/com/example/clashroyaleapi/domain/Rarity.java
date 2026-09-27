package com.example.clashroyaleapi.domain;

import java.util.List;

/** カードのレアリティの並び順。カード一覧とカードコレクションで順番が食い違わないよう1箇所にまとめる。 */
public final class Rarity {

    /** ゲーム内の並びに合わせた順(公式APIの値)。 */
    private static final List<String> ORDER = List.of("common", "rare", "epic", "legendary", "champion");

    private Rarity() {
    }

    /** ここに無い新しいレアリティは末尾に回す。 */
    public static int rankOf(String rarity) {
        int index = ORDER.indexOf(rarity);
        return index < 0 ? ORDER.size() : index;
    }
}
