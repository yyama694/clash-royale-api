package com.example.clashroyaleapi.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportedLanguagesTest {

    private static Locale fromHeader(String acceptLanguage) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (acceptLanguage != null) {
            request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
        }
        return SupportedLanguages.fromAcceptLanguage(request);
    }

    @Test
    void 地域付きの指定も言語で照合する() {
        assertEquals(Locale.JAPANESE, SupportedLanguages.match(Locale.JAPAN).orElseThrow());
        assertEquals(Locale.ENGLISH, SupportedLanguages.match(Locale.forLanguageTag("en-GB")).orElseThrow());
        assertTrue(SupportedLanguages.match(Locale.FRENCH).isEmpty());
        assertTrue(SupportedLanguages.match(null).isEmpty());
    }

    @Test
    void 先頭が未対応言語でも後ろの候補に対応言語があればそれを選ぶ() {
        assertEquals(Locale.JAPANESE, fromHeader("fr,ja;q=0.9"));
        assertEquals(Locale.JAPANESE, fromHeader("zh-TW,ja;q=0.8"));
        assertEquals(Locale.ENGLISH, fromHeader("de-DE,en;q=0.5,ja;q=0.3"));
    }

    @Test
    void q値の順に選ぶ() {
        assertEquals(Locale.ENGLISH, fromHeader("ja;q=0.5,en;q=0.9"));
    }

    @Test
    void 希望が無ければ日本語_対応言語が一つも無ければ英語() {
        assertEquals(Locale.JAPANESE, fromHeader(null));
        assertEquals(Locale.JAPANESE, fromHeader(" "));
        assertEquals(Locale.ENGLISH, fromHeader("fr"));
    }

    @Test
    void 壊れたヘッダは希望なしとして扱う() {
        assertEquals(Locale.JAPANESE, fromHeader("ja;q=abc"));
    }

    @Test
    void 表示用ロケールは対応言語に寄せる() {
        assertEquals(Locale.JAPANESE, SupportedLanguages.displayLocale(Locale.JAPAN));
        assertEquals(Locale.ENGLISH, SupportedLanguages.displayLocale(Locale.GERMAN));
    }
}
