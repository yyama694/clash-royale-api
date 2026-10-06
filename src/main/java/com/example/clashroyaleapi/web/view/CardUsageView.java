package com.example.clashroyaleapi.web.view;

import java.util.List;

/**
 * カード詳細画面の「世界トップのデッキでの使用率」。
 * percent は小数第1位で丸めた値(表示言語の小数点で出すため、文字列にせず数値のまま渡す)。
 * rank は誰も使っていなかったカードでは null になり、画面では順位の行ごと出さない。
 * seasonNotice は前のシーズンの最終順位の上位で集めたときの断り書き。今シーズンのランキングで集めたときは null。
 */
public record CardUsageView(double percent, int users, int sampleSize, Integer rank, int rankedOf,
        TimeView collectedAt, String seasonNotice, List<PartnerView> partners) {

    public record PartnerView(int id, String name, String iconUrl, int percent) {
    }
}
