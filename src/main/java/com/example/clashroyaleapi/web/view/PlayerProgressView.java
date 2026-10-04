package com.example.clashroyaleapi.web.view;

/**
 * プレイヤー情報画面の「前回見たときからの変化」の元になる値。比べる相手(前回の値)はブラウザの localStorage にあるので、
 * 差はブラウザで計算する。rank・rankedRating はランク戦の順位があるときだけ値を持つ(画面上部の表示と同じ条件)。
 */
public record PlayerProgressView(int trophies, int wins, int losses, Integer rank, Integer rankedRating) {
}
