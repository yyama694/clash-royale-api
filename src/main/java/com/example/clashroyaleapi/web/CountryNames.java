package com.example.clashroyaleapi.web;

import com.ibm.icu.text.Collator;
import com.ibm.icu.text.DisplayContext;
import com.ibm.icu.text.LocaleDisplayNames;
import com.ibm.icu.util.ULocale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * 公式APIの国・地域を表示用の名前にする。訳はCLDR(ICU)に任せ、200件超の訳語を自前で持たない。
 * JDKの getDisplayCountry は正式名称しか返さず「中華人民共和国香港特別行政区」がタブ見出しに出てしまうため、
 * ICUの短い表記(香港)を使う。
 */
@Component
public class CountryNames {

    /**
     * 公式APIの国以外の地域(countryCodeが無い)を、CLDRの地域コード(UN M49)に対応付ける。
     * "International" はCLDRに該当が無く、ゲーム内の公式な日本語表記も確認できていないため、APIの英語名のまま出す。
     */
    private static final Map<String, String> REGION_CODES_BY_API_NAME = Map.of(
            "Europe", "150",
            "North America", "003",
            "South America", "005",
            "Asia", "142",
            "Oceania", "009",
            "Africa", "002",
            "Unknown", "ZZ");

    private final MessageSource messageSource;

    public CountryNames(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** CLDRで訳せないコード(公式API独自のもの等)は、公式APIの英語名にフォールバックする。 */
    public String countryName(String countryCode, String apiName, Locale locale) {
        String displayName = displayNames(locale).regionDisplayName(countryCode);
        return displayName == null || displayName.isBlank() || displayName.equals(countryCode) ? apiName : displayName;
    }

    public String locationName(String countryCode, String apiName, Locale locale) {
        if (countryCode != null && !countryCode.isBlank()) {
            return countryName(countryCode, apiName, locale);
        }
        String regionCode = apiName == null ? null : REGION_CODES_BY_API_NAME.get(apiName);
        return regionCode == null ? apiName : countryName(regionCode, apiName, locale);
    }

    /**
     * 国名の並び順。日本語のCollatorは漢字の読みを知らず、「日本」「中国」などが五十音の後ろにまとめて並ぶため、
     * 漢字で始まる国名だけ読み仮名(country.reading.*)で比較する。読み仮名が無い言語では表示名で比べる。
     */
    public <T> Comparator<T> byDisplayOrder(Function<T, String> countryCode, Function<T, String> displayName,
            Locale locale) {
        Collator collator = Collator.getInstance(ULocale.forLocale(SupportedLanguages.displayLocale(locale)));
        return Comparator.comparing(item -> sortKey(countryCode.apply(item), displayName.apply(item), locale),
                collator);
    }

    String sortKey(String countryCode, String displayName, Locale locale) {
        return messageSource.getMessage("country.reading." + countryCode, null, displayName,
                SupportedLanguages.displayLocale(locale));
    }

    private static LocaleDisplayNames displayNames(Locale locale) {
        // 画面が対応していない言語(de等)の国名が英語画面に混ざらないよう、表示言語に寄せる。
        return LocaleDisplayNames.getInstance(ULocale.forLocale(SupportedLanguages.displayLocale(locale)),
                DisplayContext.LENGTH_SHORT);
    }
}
