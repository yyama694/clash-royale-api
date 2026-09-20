package com.example.clashroyaleapi.web.view;

/**
 * カード画像に重ねるエリクサーの水滴バッジ。
 * text はゲーム内と同じ表記で、鏡のように固定コストを持たないカードでは "?" になる。
 * label は水滴が読み上げられないため用意した代替テキスト。
 */
public record ElixirBadgeView(String text, String label) {
}
