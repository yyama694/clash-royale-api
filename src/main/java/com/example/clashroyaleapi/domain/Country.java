package com.example.clashroyaleapi.domain;

/**
 * ランキングの対象にできる国・地域。
 * locationId は公式APIのlocationIdで、国コードとは別物のためそのまま保持する。
 */
public record Country(String locationId, String countryCode, String englishName) {
}
