package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.config.PlayerIndexProperties;
import com.example.clashroyaleapi.config.WebConfig;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.LocaleContextResolver;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageInterceptorTest {

    private final GlobalModelAttributes modelAttributes =
            new GlobalModelAttributes(new PlayerIndexProperties("data", false));
    private final LocaleResolver localeResolver = new WebConfig(modelAttributes).localeResolver();
    private final LanguageInterceptor interceptor = new LanguageInterceptor(localeResolver, modelAttributes);

    @Test
    void 言語メニューで選んだ言語はCookieに保存してlang付きのURLへ転送する() {
        MockHttpServletRequest request = request("/clan/ABC", "sortBy=name&setlang=ja");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));

        assertEquals(303, response.getStatus());
        assertEquals("/clan/ABC?sortBy=name&lang=ja", response.getHeader(HttpHeaders.LOCATION));
        assertEquals("ja", response.getCookie(WebConstants.LANGUAGE_PARAM).getValue());
    }

    @Test
    void 対応外の言語は保存せずsetlangを外したURLへ転送する() {
        MockHttpServletRequest request = request("/", "setlang=pl");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));

        assertEquals("/", response.getHeader(HttpHeaders.LOCATION));
        assertNull(response.getCookie(WebConstants.LANGUAGE_PARAM));
    }

    @Test
    void 存在しないURLのエラー画面では保存だけして転送しない() {
        MockHttpServletRequest request = request("/error", "setlang=de");
        request.setDispatcherType(DispatcherType.ERROR);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));

        assertEquals("de", response.getCookie(WebConstants.LANGUAGE_PARAM).getValue());
        assertEquals(Locale.GERMAN, localeResolver.resolveLocale(request));
    }

    @Test
    void langはその画面だけに使いCookieより優先し保存しない() {
        MockHttpServletRequest request = request("/player/ABC", "lang=ja&from=share");
        request.setCookies(new Cookie(WebConstants.LANGUAGE_PARAM, "en"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));

        assertEquals(Locale.JAPANESE, localeResolver.resolveLocale(request));
        // DispatcherServletは画面全体のロケールをこちらで決める。
        assertEquals(Locale.JAPANESE,
                ((LocaleContextResolver) localeResolver).resolveLocaleContext(request).getLocale());
        assertNull(response.getCookie(WebConstants.LANGUAGE_PARAM));
    }

    @Test
    void langが無いか対応外ならCookieの言語を使う() {
        MockHttpServletRequest request = request("/player/ABC", "lang=pl");
        request.setCookies(new Cookie(WebConstants.LANGUAGE_PARAM, "es"));

        assertEquals(Locale.forLanguageTag("es"), localeResolver.resolveLocale(request));
    }

    private static MockHttpServletRequest request(String uri, String query) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setQueryString(query);
        for (String pair : query.split("&")) {
            String[] keyValue = pair.split("=", 2);
            request.addParameter(keyValue[0], keyValue[1]);
        }
        return request;
    }
}
