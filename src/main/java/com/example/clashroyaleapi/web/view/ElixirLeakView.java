package com.example.clashroyaleapi.web.view;

/**
 * 戦績サマリーの消費漏れ。中央値は、勝った(負けた)対戦が少なければ null(画面は「-」)。
 *
 * @param enough     本人の勝った対戦・負けた対戦のどちらかで中央値を出せたか。false なら表は出さず案内文を出す
 * @param topBattles 世界上位どうしのランク戦の数。まだ集めていなければ null で、比較の行を出さない
 * @param topPlayers 世界上位何人どうしの対戦か
 */
public record ElixirLeakView(boolean enough, int battles, Double winMedian, Double loseMedian, Integer topBattles,
        Double topWinMedian, Double topLoseMedian, int topPlayers) {
}
