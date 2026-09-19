package com.example.clashroyaleapi.domain;

import java.util.List;

/**
 * 名前検索の結果1ページ分。
 *
 * @param exact       完全一致した人のうち、このページの分(最近確認した順)
 * @param exactTotal  完全一致した人の総数
 * @param offset      exact の先頭が完全一致の何人目か(0始まり)
 * @param prefix      前方一致の人(名前順)。完全一致だけでページが埋まらないときの残りの枠の分
 * @param morePrefix  前方一致の人のうち、表示しきれなかった人がいるか
 */
public record PlayerNameSearch(List<PlayerNameMatch> exact, int exactTotal, int offset,
        List<PlayerNameMatch> prefix, boolean morePrefix) {

    public boolean isEmpty() {
        return exactTotal == 0 && prefix.isEmpty() && !morePrefix;
    }
}
