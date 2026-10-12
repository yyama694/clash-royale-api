package com.example.clashroyaleapi.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeCookieTest {

    private final MeCookie meCookie = new MeCookie();

    @Test
    void タグの形のときだけ読み_それ以外は無かったものとする() {
        assertEquals(Optional.of("2ABC123"), meCookie.read(withCookie("2ABC123")));
        assertEquals(Optional.empty(), meCookie.read(withCookie("#2ABC123")));
        assertEquals(Optional.empty(), meCookie.read(withCookie("abc/../x")));
        assertEquals(Optional.empty(), meCookie.read(withCookie("")));
        assertEquals(Optional.empty(), meCookie.read(new MockHttpServletRequest()));
    }

    @Test
    void 保存は1年_解除は同じ名前で期限0のCookieを返す() {
        MockHttpServletResponse saved = new MockHttpServletResponse();
        meCookie.write(saved, "2ABC123");
        Cookie cookie = saved.getCookie(MeCookie.NAME);
        assertEquals("2ABC123", cookie.getValue());
        assertEquals(365 * 24 * 60 * 60, cookie.getMaxAge());
        assertTrue(cookie.isHttpOnly());

        MockHttpServletResponse cleared = new MockHttpServletResponse();
        meCookie.clear(cleared);
        assertEquals(0, cleared.getCookie(MeCookie.NAME).getMaxAge());
    }

    private static MockHttpServletRequest withCookie(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(MeCookie.NAME, value));
        return request;
    }
}
