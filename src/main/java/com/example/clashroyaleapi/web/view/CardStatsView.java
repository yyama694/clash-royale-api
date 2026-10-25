package com.example.clashroyaleapi.web.view;

import java.util.List;

/**
 * カード詳細画面の「ステータス」欄。レベル16とレベル11の値を両方持ち、画面で切り替える。
 *
 * @param notice 読み取った日と、計算で出した値についての断り書き
 */
public record CardStatsView(String notice, List<Section> sections) {

    /** @param heading 通常のステータスは見出しを付けないので null */
    public record Section(String heading, String lead, List<Row> rows) {
    }

    /** @param level11 求められない項目は null(画面では「—」) */
    public record Row(String label, String level16, String level11) {
    }
}
