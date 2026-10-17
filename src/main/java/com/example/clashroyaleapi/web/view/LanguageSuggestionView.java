package com.example.clashroyaleapi.web.view;

/** 着地した画面の上に出す「〇〇語で表示」。text は勧める言語(code)で書いた文言。 */
public record LanguageSuggestionView(String code, String text) {
}
