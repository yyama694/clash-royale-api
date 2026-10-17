package com.example.clashroyaleapi.web.view;

/**
 * 個人ランキングの24時間の順位の変化と、カードの使用率の約1週間の増減。
 * text は表に出す短い表示(▲3・▼2・NEW・－)、label は読み上げとマウスを重ねたときの説明。
 * direction は色分け用のクラス名(up・down・new・same)。
 */
public record RankChangeView(String text, String label, String direction) {
}
