package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.config.HumanCheckProperties;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumanCheckControllerTest {

    private static final String IP = "203.0.113.10";
    private static final HumanPass HUMAN_PASS = new HumanPass("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII),
            4, Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC));

    private final HumanCheckController withButton = new HumanCheckController(HUMAN_PASS, new HumanCheckProperties(true, true, 4, true));
    private final HumanCheckController withoutButton = new HumanCheckController(HUMAN_PASS, new HumanCheckProperties(true, true, 4, false));

    private static MockHttpServletRequest post() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/human-check");
        request.setRemoteAddr(IP);
        return request;
    }

    private static String answer(String challenge) throws Exception {
        for (int a = 0; ; a++) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((challenge + ":" + a).getBytes(StandardCharsets.UTF_8));
            if ((digest[0] & 0xf0) == 0) {
                return Integer.toString(a);
            }
        }
    }

    private Cookie solve(HumanCheckController controller) throws Exception {
        String challenge = HUMAN_PASS.challenge(IP).orElseThrow();
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertEquals(204, controller.solve(challenge, answer(challenge), post(), response).getStatusCode().value());
        return response.getCookie(HumanCheckInterceptor.COOKIE);
    }

    @Test
    void ボタンの段があるときは_計算を解いてもボタン待ちの印しか付けない() throws Exception {
        Cookie cookie = solve(withButton);
        assertTrue(HUMAN_PASS.isAwaitingPress(IP, cookie.getValue()));
        assertFalse(HUMAN_PASS.isValidPass(IP, cookie.getValue()));
        assertEquals(600, cookie.getMaxAge());
        assertTrue(cookie.isHttpOnly());
    }

    @Test
    void ボタンの段が無いときは_計算を解けば通行証を付ける() throws Exception {
        Cookie cookie = solve(withoutButton);
        assertTrue(HUMAN_PASS.isValidPass(IP, cookie.getValue()));
        assertEquals(30 * 24 * 60 * 60, cookie.getMaxAge());
    }

    @Test
    void ボタンを押すと_ボタン待ちの印を通行証に替える() throws Exception {
        MockHttpServletRequest request = post();
        request.setCookies(solve(withButton));
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertEquals(204, withButton.press("1234", request, response).getStatusCode().value());
        Cookie pass = response.getCookie(HumanCheckInterceptor.COOKIE);
        assertTrue(HUMAN_PASS.isValidPass(IP, pass.getValue()));
        assertEquals(30 * 24 * 60 * 60, pass.getMaxAge());
    }

    @Test
    void 押すまでの時間が無い_印が無い_通行証を印の代わりに送ったときは断る() throws Exception {
        Cookie awaitingPress = solve(withButton);
        for (String waited : new String[] {null, "", "abc", "12345678", "-1"}) {
            MockHttpServletRequest request = post();
            request.setCookies(awaitingPress);
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertEquals(400, withButton.press(waited, request, response).getStatusCode().value(), String.valueOf(waited));
            assertNull(response.getCookie(HumanCheckInterceptor.COOKIE));
        }
        assertEquals(400, withButton.press("1234", post(), new MockHttpServletResponse()).getStatusCode().value());
        MockHttpServletRequest withPass = post();
        withPass.setCookies(solve(withoutButton));
        assertEquals(400, withButton.press("1234", withPass, new MockHttpServletResponse()).getStatusCode().value());
    }
}
