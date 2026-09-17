package com.example.clashroyaleapi.web;

import java.util.regex.Pattern;

/**
 * プレイヤー名・クラン名を表示用に整える。
 * ゲーム内では名前に文字色を変えるタグ(例: "&lt;c6&gt;Ale :D")を含められ、公式APIはそれをそのまま返す。
 * th:text がエスケープするので安全上の問題は無いが、画面にタグが文字として出てしまうため取り除く。
 */
public final class DisplayNames {

    private static final Pattern COLOR_TAG = Pattern.compile("</?c\\d*>");

    private DisplayNames() {
    }

    public static String of(String rawName) {
        if (rawName == null) {
            return null;
        }
        String stripped = COLOR_TAG.matcher(rawName).replaceAll("").strip();
        // タグだけの名前は空になるため、何も表示されないよりは元の値を出す。
        return stripped.isEmpty() ? rawName : stripped;
    }
}
