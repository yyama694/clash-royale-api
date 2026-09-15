package com.example.clashroyaleapi.domain;

import java.util.Locale;

/**
 * 対戦の勝敗。クラウン数の比較というルールをここ1箇所に閉じ込め、
 * 集計側とテンプレート側で同じ判定を書かないようにする。
 */
public enum BattleResult {

    WIN,
    LOSE,
    DRAW;

    public static BattleResult of(int selfCrowns, int opponentCrowns) {
        if (selfCrowns > opponentCrowns) {
            return WIN;
        }
        return selfCrowns < opponentCrowns ? LOSE : DRAW;
    }

    /** メッセージキーとCSSクラス名の両方に使う小文字表記。 */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
