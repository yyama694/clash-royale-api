package com.example.clashroyaleapi.web.view;

import java.util.List;

/**
 * プレイヤー情報画面の「今シーズンの順位の推移」のグラフ。
 * 線はSVG(0〜1000の座標を縦横に引き伸ばす)、目盛りと最新の点はHTMLで重ねるので、位置は描画域に対する割合(%)で持つ。
 * 位置の文字列は小数点を「.」にしてある(CSSにそのまま書くため。表示言語の書式にしない)。
 *
 * @param segments    線の points 属性。1000位より下にいた間などで記録が途切れたところで分ける
 * @param yTicks      順位の目盛り(上が1位)
 * @param xTicks      日付(UTC)の目盛り
 * @param endLabel    最新の点に添える順位
 * @param endBelow    最新の点が上端に近く、順位を点の下に出すとき true
 * @param best        今シーズンの最高順位とその日
 * @param pointsJson  ホバーの読み取り用。[[UNIX秒, 順位, レーティング], ...]
 * @param pointLabel  ホバーの文言。{rank}・{rating} を数字に置き換えて使う
 * @param rankTop     縦軸の上端の順位
 * @param rankBottom  縦軸の下端の順位
 */
public record RankHistoryView(List<String> segments, List<Tick> yTicks, List<Tick> xTicks, String endX, String endY,
                              String endLabel, boolean endBelow, String best, String pointsJson, String pointLabel,
                              int rankTop, int rankBottom) {

    /** @param position 描画域の上端(縦軸)・左端(横軸)からの割合(%) */
    public record Tick(String position, String svgPosition, String label) {
    }
}
