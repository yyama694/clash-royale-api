package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.domain.Country;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CountryPreferenceTest {

    private static final List<Country> COUNTRIES = List.of(
            new Country("57000122", "JP", "Japan"),
            new Country("57000249", "US", "United States"),
            new Country("57000094", "DE", "Germany"));

    private CountryPreference countryPreference;

    @BeforeEach
    void setUp() {
        countryPreference = new CountryPreference();
    }

    @Test
    void クエリでの明示指定が最優先() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(WebConstants.COUNTRY_PARAM, "US"));
        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "de-DE");

        assertEquals("JP", countryPreference.resolve("jp", request, COUNTRIES, Locale.JAPANESE).orElseThrow().countryCode());
    }

    @Test
    void 指定が無ければ前回選択のCookieを使う() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(WebConstants.COUNTRY_PARAM, "US"));
        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "de-DE");

        assertEquals("US", countryPreference.resolve(null, request, COUNTRIES, Locale.JAPANESE).orElseThrow().countryCode());
    }

    @Test
    void 初回訪問はAccept_Languageの国を使う() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "de-DE,de;q=0.9,en;q=0.8");

        assertEquals("DE", countryPreference.resolve(null, request, COUNTRIES, Locale.JAPANESE).orElseThrow().countryCode());
    }

    @Test
    void 地域の無い言語はその言語の代表的な国と推定する() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "en");

        assertEquals("US", countryPreference.resolve(null, request, COUNTRIES, Locale.JAPANESE).orElseThrow().countryCode());
    }

    @Test
    void 推定した国がランキングの対象外なら次の候補を見る() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // frから推定するフランスは国一覧(COUNTRIES)に無いので、次のde-DEが使われる。
        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "fr,de-DE;q=0.8");

        assertEquals("DE", countryPreference.resolve(null, request, COUNTRIES, Locale.JAPANESE).orElseThrow().countryCode());
    }

    @Test
    void 国を特定できない場合は表示言語の代表的な国を使う() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "*");

        assertEquals("JP", countryPreference.resolve(null, request, COUNTRIES, Locale.JAPANESE).orElseThrow().countryCode());
        assertEquals("US", countryPreference.resolve(null, request, COUNTRIES, Locale.ENGLISH).orElseThrow().countryCode());
    }

    @Test
    void 表示言語の国もランキングの対象外なら日本にフォールバックする() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "*");
        List<Country> withoutUs = List.of(COUNTRIES.get(0), COUNTRIES.get(2));

        assertEquals("JP", countryPreference.resolve(null, request, withoutUs, Locale.ENGLISH).orElseThrow().countryCode());
    }

    @Test
    void 未対応の国コードを指定されても他の手段で解決する() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(WebConstants.COUNTRY_PARAM, "US"));

        assertEquals("US", countryPreference.resolve("ZZ", request, COUNTRIES, Locale.JAPANESE).orElseThrow().countryCode());
    }

    @Test
    void 国一覧が空なら選択なしになる() {
        assertTrue(countryPreference.resolve("JP", new MockHttpServletRequest(), List.of(), Locale.JAPANESE).isEmpty());
    }

    @Test
    void 選択した国をCookieに保存する() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        countryPreference.remember("DE", response);

        Cookie cookie = response.getCookie(WebConstants.COUNTRY_PARAM);
        assertEquals("DE", cookie.getValue());
        assertEquals("/", cookie.getPath());
    }
}
