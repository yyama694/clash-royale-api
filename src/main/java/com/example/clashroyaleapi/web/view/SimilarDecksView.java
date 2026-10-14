package com.example.clashroyaleapi.web.view;

import java.util.List;

/**
 * プレイヤー情報画面の「同じデッキの世界ランカー」。cardIds はデッキ画面の絞り込みへのリンク用。
 * selfExcluded は、本人も世界トップ層に入っていて数えなかったとき true(リンク先では本人も数えられるため断る)。
 */
public record SimilarDecksView(String text, List<Integer> cardIds, boolean selfExcluded) {
}
