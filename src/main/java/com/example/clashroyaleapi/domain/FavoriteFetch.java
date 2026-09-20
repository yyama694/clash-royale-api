package com.example.clashroyaleapi.domain;

/**
 * お気に入り画面で1件(プレイヤーまたはクラン)を取得した結果。取得できた/見つからない/取得できないの3通り。
 * プレイヤーとクランで同じ形になるため型引数で共用する(取得できたときの中身だけ型が違う)。
 */
public sealed interface FavoriteFetch<T> {

    String tag();

    String name();

    record Found<T>(String tag, String name, T value) implements FavoriteFetch<T> {
    }

    /** 404(アカウント削除・クラン解散など)。自動では外さない。 */
    record NotFound<T>(String tag, String name) implements FavoriteFetch<T> {
    }

    /** それ以外の失敗(429・接続失敗など)。 */
    record Unavailable<T>(String tag, String name) implements FavoriteFetch<T> {
    }
}
