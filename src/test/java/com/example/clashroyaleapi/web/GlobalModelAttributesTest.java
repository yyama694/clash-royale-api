package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.config.PlayerIndexProperties;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private static MockHttpServletRequest request(String uri, String query) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setQueryString(query);
        return request;
    }
}
