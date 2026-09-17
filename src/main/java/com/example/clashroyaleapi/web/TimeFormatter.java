package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.ApiTimestamp;
import com.example.clashroyaleapi.web.view.TimeView;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * 公式APIの日時(常にUTC)を表示用にする。
 * 閲覧者のタイムゾーンはサーバーから分からない(Accept-Languageは言語であって居場所ではない)ため、
 * 変換はブラウザ側の Intl.DateTimeFormat に任せ、ここではその材料と、スクリプトが動かない場合の表記を作る。
 */
@Component
public class TimeFormatter {

    private final MessageSource messageSource;

    public TimeFormatter(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public TimeView apiTimestamp(String raw, Locale locale) {
        return ApiTimestamp.parse(raw)
                .map(instant -> instant(instant, locale))
                .orElseGet(() -> new TimeView(null, raw));
    }

    public TimeView instant(Instant instant, Locale locale) {
        Locale displayLocale = SupportedLanguages.displayLocale(locale);
        String utcText = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(displayLocale)
                .withZone(ZoneOffset.UTC)
                .format(instant);
        return new TimeView(instant.toString(),
                messageSource.getMessage("time.utc", new Object[] {utcText}, displayLocale));
    }
}
