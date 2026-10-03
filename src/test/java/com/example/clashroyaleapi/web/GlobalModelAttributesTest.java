package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.config.PlayerIndexProperties;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlobalModelAttributesTest {

    private final GlobalModelAttributes attributes = new GlobalModelAttributes(new PlayerIndexProperties("data", false));

    @Test
    void クエリが無ければパスだけを返す() {
        assertEquals("/ranking", attributes.currentUri(request("/ranking", null)));
    }

    @Test
    void langだけを取り除き他のクエリは残す() {
        assertEquals("/clan/ABC?sortBy=name&sortDir=desc",
                attributes.currentUri(request("/clan/ABC", "sortBy=name&lang=en&sortDir=desc")));
    }

    @Test
    void langしか無ければパスだけを返す() {
        assertEquals("/", attributes.currentUri(request("/", "lang=ja")));
    }

    @Test
    void 名前がlangで始まる別のパラメータは取り除かない() {
        assertEquals("/?language=x", attributes.currentUri(request("/", "language=x&lang=en")));
    }

    @Test
    void エラー画面では転送前のURLを返す() {
        MockHttpServletRequest request = request("/error", "x=1&lang=en");
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/nonexistent");

        assertEquals("/nonexistent?x=1", attributes.currentUri(request));
    }

    @Test
    void 流入元タグのfromも取り除く() {
        assertEquals("/decks?card=26000021",
                attributes.currentUri(request("/decks", "from=x&card=26000021&lang=ja")));
        assertEquals("/player/ABC", attributes.currentUri(request("/player/ABC", "lang=ja&from=xreply")));
    }

    @Test
    void 正規URLはfromを除き対応言語のlangだけを残す() {
        assertEquals("http://localhost/decks?card=26000021&lang=ja",
                attributes.canonicalUrl(request("/decks", "from=x&card=26000021&lang=ja")));
        assertEquals("http://localhost/player/ABC?lang=es",
                attributes.canonicalUrl(request("/player/ABC", "lang=es&from=xreply")));
        assertEquals("http://localhost/player/ABC", attributes.canonicalUrl(request("/player/ABC", "from=share")));
        assertEquals("http://localhost/", attributes.canonicalUrl(request("/", "lang=xx")));
    }

    @Test
    void エラー画面には正規URLを付けない() {
        MockHttpServletRequest request = request("/error", null);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/nonexistent");

        assertNull(attributes.canonicalUrl(request));
    }

    private static MockHttpServletRequest request(String uri, String query) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setQueryString(query);
        if (query != null) {
            for (String pair : query.split("&")) {
                String[] keyValue = pair.split("=", 2);
                request.addParameter(keyValue[0], keyValue.length > 1 ? keyValue[1] : "");
            }
        }
        return request;
    }
}
