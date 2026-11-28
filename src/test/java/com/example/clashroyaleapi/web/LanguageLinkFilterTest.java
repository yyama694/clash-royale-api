package com.example.clashroyaleapi.web;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageLinkFilterTest {

    @Test
    void sitemapに載せている画面へのリンクに言語を付ける() {
        assertEquals("/card/26000021?lang=ja", LanguageLinkFilter.withLanguage("/card/26000021", "ja"));
        assertEquals("/?lang=ja", LanguageLinkFilter.withLanguage("/", "ja"));
        assertEquals("/cards?sort=usage&lang=ja", LanguageLinkFilter.withLanguage("/cards?sort=usage", "ja"));
        assertEquals("/ranking/players/seasons/2026-11?lang=es",
                LanguageLinkFilter.withLanguage("/ranking/players/seasons/2026-11", "es"));
        assertEquals("/decks?card=26000021&card=26000043&lang=ko",
                LanguageLinkFilter.withLanguage("/decks?card=26000021&card=26000043", "ko"));
        assertEquals("/balance/2026-11-03?lang=de", LanguageLinkFilter.withLanguage("/balance/2026-11-03", "de"));
    }

    @Test
    void ページ内の位置の指定は言語の後ろに残す() {
        assertEquals("/card/26000021?lang=ja#counters", LanguageLinkFilter.withLanguage("/card/26000021#counters", "ja"));
        assertEquals("/cards?sort=usage&lang=ja#top", LanguageLinkFilter.withLanguage("/cards?sort=usage#top", "ja"));
    }

    @Test
    void プレイヤー_クランと画面でないものには付けない() {
        assertEquals("/player/2LC0Q90VJ", LanguageLinkFilter.withLanguage("/player/2LC0Q90VJ", "ja"));
        assertEquals("/clan/LVCU2UJU", LanguageLinkFilter.withLanguage("/clan/LVCU2UJU", "ja"));
        assertEquals("/player/search", LanguageLinkFilter.withLanguage("/player/search", "ja"));
        assertEquals("/favorites", LanguageLinkFilter.withLanguage("/favorites", "ja"));
        assertEquals("/css/style-abc123.css", LanguageLinkFilter.withLanguage("/css/style-abc123.css", "ja"));
        assertEquals("/cardsx", LanguageLinkFilter.withLanguage("/cardsx", "ja"));
    }

    @Test
    void 絶対URLと言語を指定済みのリンクはそのまま() {
        assertEquals("https://princess-tower.duckdns.org/card/26000021",
                LanguageLinkFilter.withLanguage("https://princess-tower.duckdns.org/card/26000021", "ja"));
        assertEquals("//example.com/card/1", LanguageLinkFilter.withLanguage("//example.com/card/1", "ja"));
        assertEquals("/decks?card=1&lang=en", LanguageLinkFilter.withLanguage("/decks?card=1&lang=en", "ja"));
        assertEquals("/card/1?setlang=ko", LanguageLinkFilter.withLanguage("/card/1?setlang=ko", "ja"));
        assertEquals("/card/1?language=x&lang=ja", LanguageLinkFilter.withLanguage("/card/1?language=x", "ja"));
    }

    @Test
    void 対応言語のlangで開いた画面のリンクだけを書き換える() throws Exception {
        assertEquals("/card/1?lang=ja", encodedInRequest("lang=ja", "/card/1"));
        assertEquals("/card/1", encodedInRequest(null, "/card/1"));
        assertEquals("/card/1", encodedInRequest("lang=xx", "/card/1"));
        assertEquals("/card/1", encodedInRequest("setlang=ja", "/card/1"));
    }

    private static String encodedInRequest(String query, String url) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/cards");
        if (query != null) {
            request.setQueryString(query);
            String[] param = query.split("=", 2);
            request.addParameter(param[0], param[1]);
        }
        AtomicReference<ServletResponse> seen = new AtomicReference<>();
        new LanguageLinkFilter().doFilter(request, new MockHttpServletResponse(),
                new MockFilterChain() {
                    @Override
                    public void doFilter(ServletRequest req, ServletResponse res) {
                        seen.set(res);
                    }
                });
        return ((HttpServletResponse) seen.get()).encodeURL(url);
    }
}
