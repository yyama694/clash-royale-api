package com.example.clashroyaleapi.web.view;

/**
 * プレイヤー情報画面の、世界の順位の無い人の今シーズンのランク戦。
 *
 * @param rating 天界でレーティングがあるときだけ。それ以外は null
 */
public record RankedLeagueView(String name, Integer rating) {
}
