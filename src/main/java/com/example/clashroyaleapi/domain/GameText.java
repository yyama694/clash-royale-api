package com.example.clashroyaleapi.domain;

import java.util.regex.Pattern;

/**
 * プレイヤー名・クラン名などに含まれる、ゲーム内の文字装飾タグを扱う。
 * 公式APIは <c2>Name のような色指定タグをそのまま返す(2026-09-17時点で上位プレイヤーの名前に多数)。
 * 実データでは <c0>〜<c9> で閉じタグが無い形が多いが、色の16進指定と閉じタグ </c> も取り除く。
 */
public final class GameText {

    private static final Pattern FORMATTING_TAG = Pattern.compile("<c[0-9A-Fa-f]*>|</c>");

    private GameText() {
    }

    public static String stripFormatting(String text) {
        if (text == null) {
            return null;
        }
        String stripped = FORMATTING_TAG.matcher(text).replaceAll("").strip();
        // タグだけの名前は空になるため、何も表示されないよりは元の値を出す。
        return stripped.isEmpty() ? text : stripped;
    }
}
