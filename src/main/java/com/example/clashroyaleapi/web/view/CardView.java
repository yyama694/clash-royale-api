package com.example.clashroyaleapi.web.view;

import com.example.clashroyaleapi.domain.CardForm;

/**
 * テンプレートに渡す時点でカード名は表示用に解決済みにする(alt属性も同じ名前で揃う)。
 * iconUrl は形に合わせた画像(進化・ヒーローの画像が無ければ通常の画像)。formLabel は通常の形なら null。
 * elixir はタワーユニットでは null。
 */
public record CardView(int id, String name, String iconUrl, int level, CardForm form, String formLabel,
        ElixirBadgeView elixir) {

    public String formClass() {
        return switch (form) {
            case EVOLUTION -> "form-evolution";
            case HERO -> "form-hero";
            case NORMAL -> null;
        };
    }
}
