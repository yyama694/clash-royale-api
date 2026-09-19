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
        assertEquals(Locale.forLanguageTag("es"), SupportedLanguages.match(Locale.forLanguageTag("es-MX")).orElseThrow());
        assertEquals(Locale.forLanguageTag("pt"), SupportedLanguages.match(Locale.forLanguageTag("pt-BR")).orElseThrow());
        assertEquals(Locale.forLanguageTag("pt"), SupportedLanguages.match(Locale.forLanguageTag("pt-PT")).orElseThrow());
        assertEquals(Locale.forLanguageTag("de"), SupportedLanguages.match(Locale.forLanguageTag("de-AT")).orElseThrow());
        assertEquals(Locale.forLanguageTag("fr"), SupportedLanguages.match(Locale.forLanguageTag("fr-CA")).orElseThrow());
        assertEquals(Locale.forLanguageTag("it"), SupportedLanguages.match(Locale.forLanguageTag("it-CH")).orElseThrow());
        assertEquals(Locale.forLanguageTag("ru"), SupportedLanguages.match(Locale.forLanguageTag("ru-KZ")).orElseThrow());
        assertTrue(SupportedLanguages.match(Locale.KOREAN).isEmpty());
        assertTrue(SupportedLanguages.match(null).isEmpty());
    }

    @Test
    void 先頭が未対応言語でも後ろの候補に対応言語があればそれを選ぶ() {
        assertEquals(Locale.JAPANESE, fromHeader("ko,ja;q=0.9"));
        assertEquals(Locale.JAPANESE, fromHeader("zh-TW,ja;q=0.8"));
        assertEquals(Locale.ENGLISH, fromHeader("ko-KR,en;q=0.5,ja;q=0.3"));
        assertEquals(Locale.forLanguageTag("pt"), fromHeader("pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7"));
        assertEquals(Locale.forLanguageTag("es"), fromHeader("ko-KR,es;q=0.8,en;q=0.5"));
        assertEquals(Locale.forLanguageTag("de"), fromHeader("de-DE,en;q=0.5,ja;q=0.3"));
        assertEquals(Locale.forLanguageTag("it"), fromHeader("it-IT,es;q=0.8,en;q=0.5"));
    }

    @Test
    void q値の順に選ぶ() {
        assertEquals(Locale.ENGLISH, fromHeader("ja;q=0.5,en;q=0.9"));
    }

    @Test
    void 希望が無い場合も対応言語が一つも無い場合も英語() {
        assertEquals(Locale.ENGLISH, fromHeader(null));
        assertEquals(Locale.ENGLISH, fromHeader(" "));
        assertEquals(Locale.ENGLISH, fromHeader("ko"));
    }

    @Test
    void 壊れたヘッダは希望なしとして扱う() {
        assertEquals(Locale.ENGLISH, fromHeader("ja;q=abc"));
    }

    @Test
    void 表示用ロケールは対応言語に寄せる() {
        assertEquals(Locale.JAPANESE, SupportedLanguages.displayLocale(Locale.JAPAN));
        assertEquals(Locale.forLanguageTag("de"), SupportedLanguages.displayLocale(Locale.GERMAN));
        assertEquals(Locale.ENGLISH, SupportedLanguages.displayLocale(Locale.KOREAN));
    }
}
