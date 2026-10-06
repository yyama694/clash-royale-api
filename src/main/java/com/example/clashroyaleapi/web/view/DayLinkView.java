package com.example.clashroyaleapi.web.view;

import java.time.LocalDate;

/** 日ごとの画面へのリンク。label は表示言語の書式の日付。 */
public record DayLinkView(LocalDate day, String label) {
}
