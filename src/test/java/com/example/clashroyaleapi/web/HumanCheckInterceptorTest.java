package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.domain.HumanPass;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumanCheckInterceptorTest {

    private static final String CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/155.0.0.0 Safari/537.36";
    private static final String IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 "
            + "(KHTML, like Gecko) Mobile/15E148 Twitter for iPhone/12.41";
    private static final HumanPass HUMAN_PASS = new HumanPass("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII),
            4, Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneOffset.UTC));
    private static final String PAGE = "<p>__CHALLENGE__ / __DIFFICULTY__</p>";

    private final HumanCheckInterceptor interceptor = new HumanCheckInterceptor(HUMAN_PASS, true, PAGE);

    private static MockHttpServletRequest get(String userAgent, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/player/2VG8GV9UV");
        if (userAgent != null) {
            request.addHeader("User-Agent", userAgent);
        }
        request.setRemoteAddr(ip);
        return request;
    }

    private static String passFor(String ip) throws Exception {
        String challenge = HUMAN_PASS.challenge(ip).orElseThrow();
        for (int a = 0; ; a++) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((challenge + ":" + a).getBytes(StandardCharsets.UTF_8));
            if ((digest[0] & 0xf0) == 0) {
                return HUMAN_PASS.solve(ip, challenge, Integer.toString(a)).orElseThrow();
            }
        }
    }

    @Test
    void 通行証の無いブラウザには確かめ用のページを返す() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(get(CHROME, "203.0.113.10"), response, null));
        assertEquals(403, response.getStatus());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        String body = response.getContentAsString();
        assertTrue(body.matches("<p>\\d+\\.[A-Za-z0-9_-]+ / 4</p>"), body);
    }

    @Test
    void UAの無いリクエストも確かめる() throws Exception {
        assertFalse(interceptor.preHandle(get(null, "203.0.113.10"), new MockHttpServletResponse(), null));
    }

    @Test
    void 同じIPの範囲の通行証があれば通し_別の範囲なら確かめ直す() throws Exception {
        Cookie pass = new Cookie(HumanCheckInterceptor.COOKIE, passFor("203.0.113.10"));
        MockHttpServletRequest same = get(CHROME, "203.0.113.77");
        same.setCookies(pass);
        assertTrue(interceptor.preHandle(same, new MockHttpServletResponse(), null));
        MockHttpServletRequest other = get(CHROME, "198.51.100.77");
        other.setCookies(pass);
        assertFalse(interceptor.preHandle(other, new MockHttpServletResponse(), null));
    }

    @Test
    void 名乗っているクローラーと自分たちのツールは通す() throws Exception {
        for (String ua : new String[] {
                "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15 (Applebot/0.1)",
                "Twitterbot/1.0",
                "facebookexternalhit/1.1;line-poker/1.0",
                "Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko; compatible; ChatGPT-User/1.0; +https://openai.com/bot)",
                "curl/8.9.1",
                "princess-tower-factcheck",
                "ranking-live/1.0"}) {
            assertTrue(interceptor.preHandle(get(ua, "203.0.113.10"), new MockHttpServletResponse(), null), ua);
        }
    }

    @Test
    void スマホは設定で対象外にできる() throws Exception {
        assertTrue(interceptor.preHandle(get(IPHONE, "203.0.113.10"), new MockHttpServletResponse(), null));
        HumanCheckInterceptor strict = new HumanCheckInterceptor(HUMAN_PASS, false, PAGE);
        assertFalse(strict.preHandle(get(IPHONE, "203.0.113.10"), new MockHttpServletResponse(), null));
    }

    @Test
    void curlを名乗っても先頭でなければ通さない() {
        assertFalse(HumanCheckInterceptor.isExempt("Mozilla/5.0 curl/8.9.1", false));
    }

    @Test
    void GETとHEAD以外は確かめない() throws Exception {
        MockHttpServletRequest post = get(CHROME, "203.0.113.10");
        post.setMethod("POST");
        assertTrue(interceptor.preHandle(post, new MockHttpServletResponse(), null));
    }

    @Test
    void IPの形でなく問題を作れないときは締め出さない() throws Exception {
        assertTrue(interceptor.preHandle(get(CHROME, "unknown"), new MockHttpServletResponse(), null));
    }
}
