package com.example.clashroyaleapi.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * ランキングの対象にできる国・地域。
 * locationId は公式APIのlocationIdで、国コードとは別物のためそのまま保持する。
 */
public record Country(String locationId, String countryCode, String englishName) {

    /** 国コードは前後の空白・小文字を許す(URLのクエリやCookieから来るため)。 */
    public static Optional<Country> find(List<Country> countries, String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return Optional.empty();
        }
        String normalized = countryCode.strip().toUpperCase(Locale.ROOT);
        return countries.stream().filter(country -> country.countryCode().equals(normalized)).findFirst();
    }
}
