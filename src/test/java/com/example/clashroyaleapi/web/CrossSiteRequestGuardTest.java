package com.example.clashroyaleapi.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossSiteRequestGuardTest {

    private final CrossSiteRequestGuard guard = new CrossSiteRequestGuard();

    @Test
    void 同じオリジンからのPOSTは通す() {
        assertTrue(preHandle(post("same-origin", null)));
        // 利用者自身の操作(アドレスバーなど)で、他サイトからは作れない。
        assertTrue(preHandle(post("none", null)));
    }

    @Test
    void 他サイトと同じサイトの別オリジンからのPOSTは拒否する() {
        assertThrows(CrossSiteRequestException.class, () -> preHandle(post("cross-site", "https://evil.example")));
        assertThrows(CrossSiteRequestException.class, () -> preHandle(post("same-site", null)));
    }

    @Test
    void Sec_Fetch_Siteを送らないブラウザではOriginで判定する() {
        // MockHttpServletRequest の自分のオリジンは http://localhost。
        assertTrue(preHandle(post(null, "http://localhost")));
        assertThrows(CrossSiteRequestException.class, () -> preHandle(post(null, "https://evil.example")));
        assertThrows(CrossSiteRequestException.class, () -> preHandle(post(null, "null")));
    }

    @Test
    void どちらのヘッダーも無いリクエストはブラウザ以外からなので通す() {
        assertTrue(preHandle(post(null, null)));
    }

    @Test
    void GETは他サイトからでも通す() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/favorites");
        request.addHeader("Sec-Fetch-Site", "cross-site");

        assertTrue(guard.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    private boolean preHandle(MockHttpServletRequest request) {
        return guard.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    private static MockHttpServletRequest post(String fetchSite, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/favorites/players/2PYL/remove");
        if (fetchSite != null) {
            request.addHeader("Sec-Fetch-Site", fetchSite);
        }
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        return request;
    }
}
