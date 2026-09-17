package com.example.clashroyaleapi.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 実際の messages*.properties を読んで、英語の単数・複数が数値どおりに出ることを確認する。
 * 1件のデータは公式APIの実データでは稀にしか出ず、画面を見ても気付けないため。
 */
class IcuMessageSourceTest {

    private IcuMessageSource messageSource;

    @BeforeEach
    void setUp() {
        messageSource = IcuMessageSource.forBasename("messages");
    }

    @Test
    void 英語は数値に応じて単数形と複数形を出し分ける() {
        assertEquals("1 crown", crowns(1, Locale.ENGLISH));
        assertEquals("2 crowns", crowns(2, Locale.ENGLISH));
        assertEquals("0 crowns", crowns(0, Locale.ENGLISH));
    }

    @Test
    void 日本語は数の区別が無いので同じ表記になる() {
        assertEquals("1 クラウン", crowns(1, Locale.JAPANESE));
        assertEquals("2 クラウン", crowns(2, Locale.JAPANESE));
    }

    @Test
    void 最終アクセスの日数もメッセージ側で単複を決める() {
        assertEquals("1 day ago", message("clan.lastSeen.ago", 1, Locale.ENGLISH));
        assertEquals("3 days ago", message("clan.lastSeen.ago", 3, Locale.ENGLISH));
        assertEquals("3日前", message("clan.lastSeen.ago", 3, Locale.JAPANESE));
    }

    private String crowns(int count, Locale locale) {
        return message("battle.crowns", count, locale);
    }

    private String message(String key, int count, Locale locale) {
        return messageSource.getMessage(key, new Object[] {count}, locale);
    }
}
