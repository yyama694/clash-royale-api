package com.example.clashroyaleapi.domain;

/**
 * 公式APIのカードレベルを、ゲーム内の表記に直す。
 * APIの{@code level}はレアリティごとに1から数え直した値で、{@code maxLevel}もレアリティごとに違う
 * (実測: コモン16・レア14・エピック11・レジェンダリー8・チャンピオン6)。
 * そのため生値をそのまま出すと、同じ強さのデッキでもレアリティによって数字がばらつき、上位プレイヤーほど低く見える。
 */
public final class CardLevel {

    /** ゲーム内のカードレベルの上限。公式APIの{@code /cards}で、コモンの{@code maxLevel}が示す値。 */
    private static final int MAX_LEVEL = 16;

    private CardLevel() {
    }

    public static int inGame(int apiLevel, int maxLevel) {
        // 上限が引き上げられた場合など想定外の値では、誤った数字を出すより生値のままにする。
        if (maxLevel <= 0 || maxLevel > MAX_LEVEL) {
            return apiLevel;
        }
        return apiLevel + (MAX_LEVEL - maxLevel);
    }
}
