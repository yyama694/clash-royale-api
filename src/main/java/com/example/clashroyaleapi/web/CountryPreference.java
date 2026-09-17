package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.domain.Country;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 「自分の国」のランキングを、訪問者ごとに決める。
 * 明示選択(クエリ) &gt; 前回の選択(Cookie) &gt; Accept-Languageの国 の順で解決し、
 * どれも当てはまらない場合だけ既定国にフォールバックする。表示言語の切り替え(lang)と同じ考え方。
 */
@Component
public class CountryPreference {

    // 主な利用者が日本のプレイヤーのため、国を特定できないときは日本を既定にする。
    private static final String FALLBACK_COUNTRY_CODE = "JP";
    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(365);

    public Optional<Country> resolve(String requested, HttpServletRequest request, List<Country> countries) {
        if (countries.isEmpty()) {
            return Optional.empty();
        }
        return find(countries, requested)
                .or(() -> find(countries, cookieValue(request)))
                .or(() -> find(countries, acceptLanguageCountry(request)))
                .or(() -> find(countries, FALLBACK_COUNTRY_CODE));
    }

    public void remember(String countryCode, HttpServletResponse response) {
        Cookie cookie = new Cookie(WebConstants.COUNTRY_PARAM, countryCode);
        cookie.setPath("/");
        cookie.setMaxAge((int) COOKIE_MAX_AGE.toSeconds());
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }

    private Optional<Country> find(List<Country> countries, String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return Optional.empty();
        }
        String normalized = countryCode.strip().toUpperCase(Locale.ROOT);
        return countries.stream().filter(country -> country.countryCode().equals(normalized)).findFirst();
    }

    private String cookieValue(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, WebConstants.COUNTRY_PARAM);
        return cookie == null ? null : cookie.getValue();
    }

    /**
     * request.getLocale() はLocaleResolverが解決した表示言語(ja/en)になり国を含まないため、
     * 国の推定にはAccept-Languageヘッダを直接見る。
     */
    private String acceptLanguageCountry(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Locale.LanguageRange.parse(header).stream()
                    .map(range -> Locale.forLanguageTag(range.getRange()).getCountry())
                    .filter(country -> !country.isEmpty())
                    .findFirst()
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            // 壊れたAccept-Languageを送ってくるクライアントがあるため、推定を諦めるだけにする。
            return null;
        }
    }
}
