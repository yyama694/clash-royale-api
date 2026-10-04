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
        assertEquals(Locale.forLanguageTag("tr"), SupportedLanguages.match(Locale.forLanguageTag("tr-TR")).orElseThrow());
        assertEquals(Locale.KOREAN, SupportedLanguages.match(Locale.KOREA).orElseThrow());
        assertTrue(SupportedLanguages.match(Locale.forLanguageTag("pl")).isEmpty());
        assertTrue(SupportedLanguages.match(null).isEmpty());
    }

    @Test
    void 先頭が未対応言語でも後ろの候補に対応言語があればそれを選ぶ() {
        assertEquals(Locale.JAPANESE, fromHeader("pl,ja;q=0.9"));
        assertEquals(Locale.JAPANESE, fromHeader("zh-TW,ja;q=0.8"));
        assertEquals(Locale.ENGLISH, fromHeader("pl-PL,en;q=0.5,ja;q=0.3"));
        assertEquals(Locale.forLanguageTag("pt"), fromHeader("pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7"));
        assertEquals(Locale.forLanguageTag("es"), fromHeader("pl-PL,es;q=0.8,en;q=0.5"));
        assertEquals(Locale.KOREAN, fromHeader("ko-KR,ko;q=0.9,en-US;q=0.8"));
        assertEquals(Locale.forLanguageTag("tr"), fromHeader("tr-TR,tr;q=0.9,en;q=0.5"));
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
        assertEquals(Locale.ENGLISH, fromHeader("pl"));
    }

    @Test
    void 壊れたヘッダは希望なしとして扱う() {
        assertEquals(Locale.ENGLISH, fromHeader("ja;q=abc"));
    }

    @Test
    void URLのlangは言語コードに完全に一致するものだけ受け付ける() {
        assertEquals(Locale.JAPANESE, SupportedLanguages.fromParameter("ja").orElseThrow());
        assertEquals(Locale.forLanguageTag("ru"), SupportedLanguages.fromParameter("ru").orElseThrow());
        // 正規URLやhreflangが言語コードだけで作られるため、地域付きの書き方は受け付けない。
        assertTrue(SupportedLanguages.fromParameter("ja-JP").isEmpty());
        assertEquals(Locale.KOREAN, SupportedLanguages.fromParameter("ko").orElseThrow());
        assertTrue(SupportedLanguages.fromParameter("pl").isEmpty());
        assertTrue(SupportedLanguages.fromParameter("").isEmpty());
        assertTrue(SupportedLanguages.fromParameter(null).isEmpty());
    }

    @Test
    void OGPの言語は対応言語ごとに代表的な国を付ける() {
        assertEquals("en_US", SupportedLanguages.ogLocale(Locale.ENGLISH));
        assertEquals("es_ES", SupportedLanguages.ogLocale(Locale.forLanguageTag("es")));
        assertEquals("pt_BR", SupportedLanguages.ogLocale(Locale.forLanguageTag("pt")));
        assertEquals("tr_TR", SupportedLanguages.ogLocale(Locale.forLanguageTag("tr")));
        assertEquals("ko_KR", SupportedLanguages.ogLocale(Locale.KOREAN));
        assertEquals("en_US", SupportedLanguages.ogLocale(Locale.forLanguageTag("pl")));
    }

    @Test
    void 文の区切りは日本語と中国語だけ空白を入れない() {
        assertEquals("", SupportedLanguages.sentenceSeparator(Locale.JAPANESE));
        assertEquals("", SupportedLanguages.sentenceSeparator(Locale.forLanguageTag("zh")));
        assertEquals("", SupportedLanguages.sentenceSeparator(Locale.forLanguageTag("zh-TW")));
        assertEquals(" ", SupportedLanguages.sentenceSeparator(Locale.KOREAN));
        assertEquals(" ", SupportedLanguages.sentenceSeparator(Locale.forLanguageTag("tr")));
        assertEquals(" ", SupportedLanguages.sentenceSeparator(Locale.ENGLISH));
    }

    @Test
    void 表示用ロケールは対応言語に寄せる() {
        assertEquals(Locale.JAPANESE, SupportedLanguages.displayLocale(Locale.JAPAN));
        assertEquals(Locale.forLanguageTag("de"), SupportedLanguages.displayLocale(Locale.GERMAN));
        assertEquals(Locale.KOREAN, SupportedLanguages.displayLocale(Locale.KOREA));
        assertEquals(Locale.ENGLISH, SupportedLanguages.displayLocale(Locale.forLanguageTag("pl")));
    }
}
