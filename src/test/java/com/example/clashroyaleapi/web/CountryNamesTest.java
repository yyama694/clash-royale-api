package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.config.IcuMessageSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CountryNamesTest {

    private CountryNames countryNames;

    @BeforeEach
    void setUp() {
        countryNames = new CountryNames(IcuMessageSource.forBasename("messages"));
    }

    @Test
    void 国名はCLDRの短い表記を使う() {
        assertEquals("香港", countryNames.countryName("HK", "Hong Kong", Locale.JAPANESE));
        assertEquals("Hong Kong", countryNames.countryName("HK", "Hong Kong", Locale.ENGLISH));
        assertEquals("パレスチナ", countryNames.countryName("PS", "Palestine", Locale.JAPANESE));
    }

    @Test
    void 対応していない表示言語の国名は英語にする() {
        assertEquals("Germany", countryNames.countryName("DE", "Germany", Locale.KOREAN));
    }

    @Test
    void CLDRで訳せないコードは公式APIの名前を使う() {
        assertEquals("Somewhere", countryNames.countryName("QQ", "Somewhere", Locale.JAPANESE));
    }

    @Test
    void 国以外の地域は対応するCLDRの地域名に訳す() {
        assertEquals("ヨーロッパ", countryNames.locationName(null, "Europe", Locale.JAPANESE));
        assertEquals("North America", countryNames.locationName("", "North America", Locale.ENGLISH));
        assertEquals("日本", countryNames.locationName("JP", "Japan", Locale.JAPANESE));
    }

    @Test
    void CLDRに該当が無い地域は公式APIの名前のまま() {
        assertEquals("International", countryNames.locationName(null, "International", Locale.JAPANESE));
    }

    @Test
    void 日本語では漢字の国名も読みの五十音順に並ぶ() {
        List<String[]> countries = List.of(
                new String[] {"JP", "日本"}, new String[] {"US", "アメリカ"}, new String[] {"CN", "中国"},
                new String[] {"DE", "ドイツ"}, new String[] {"KR", "韓国"});
        Function<String[], String> code = row -> row[0];
        Function<String[], String> name = row -> row[1];

        List<String> sorted = countries.stream()
                .sorted(countryNames.byDisplayOrder(code, name, Locale.JAPANESE))
                .map(name)
                .toList();

        assertEquals(List.of("アメリカ", "韓国", "中国", "ドイツ", "日本"), sorted);
    }
}
