package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.domain.Country;

import com.ibm.icu.util.ULocale;
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
 * どれも当てはまらない場合だけ既定言語(英語)の代表的な国にフォールバックする。表示言語の切り替え(lang)と同じ考え方。
 */
@Component
public class CountryPreference {

    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(365);

    public Optional<Country> resolve(String requested, HttpServletRequest request, List<Country> countries,
            Locale displayLocale) {
        if (countries.isEmpty()) {
            return Optional.empty();
        }
        return Country.find(countries, requested)
                .or(() -> Country.find(countries, cookieValue(request)))
                .or(() -> acceptLanguageCountries(request).stream()
                        .map(countryCode -> Country.find(countries, countryCode))
                        .flatMap(Optional::stream)
                        .findFirst())
                .or(() -> Country.find(countries, displayLanguageCountry(displayLocale)))
                .or(() -> Country.find(countries, displayLanguageCountry(SupportedLanguages.INTERNATIONAL)))
                // 選択なしにすると国別タブ自体が消え、国を選び直す手段が無くなるため、必ずどれかの国にする。
                .or(() -> Optional.of(countries.get(0)));
    }

    public void remember(String countryCode, HttpServletResponse response) {
        Cookie cookie = new Cookie(WebConstants.COUNTRY_PARAM, countryCode);
        cookie.setPath("/");
        cookie.setMaxAge((int) COOKIE_MAX_AGE.toSeconds());
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }

    /**
     * Accept-Languageに地域が無い(en だけ、など)訪問者に、言語と関係なく日本を出さないための推定。
     * 表示言語の代表的な国をCLDRの対応表で求める(ja→JP、en→US)。
     */
    private String displayLanguageCountry(Locale displayLocale) {
        Locale supported = SupportedLanguages.displayLocale(displayLocale);
        return ULocale.addLikelySubtags(ULocale.forLocale(supported)).getCountry();
    }

    private String cookieValue(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, WebConstants.COUNTRY_PARAM);
        return cookie == null ? null : cookie.getValue();
    }

    /**
     * request.getLocale() はLocaleResolverが解決した表示言語(ja/en)になり国を含まないため、
     * 国の推定にはAccept-Languageヘッダを直接見る。
     * 地域が付いていない言語(en、fr等)は、CLDRの対応表(likely subtags)でその言語の代表的な国を推定する
     * (en→US、fr→FR、zh-Hant→TW)。希望の高い順に並べて返す。
     */
    private List<String> acceptLanguageCountries(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
        if (header == null || header.isBlank()) {
            return List.of();
        }
        try {
            return Locale.LanguageRange.parse(header).stream()
                    .map(Locale.LanguageRange::getRange)
                    .filter(range -> !range.startsWith("*"))
                    .map(range -> ULocale.addLikelySubtags(ULocale.forLanguageTag(range)).getCountry())
                    .filter(country -> !country.isEmpty())
                    .toList();
        } catch (IllegalArgumentException e) {
            // 壊れたAccept-Languageを送ってくるクライアントがあるため、推定を諦めるだけにする。
            return List.of();
        }
    }
}
