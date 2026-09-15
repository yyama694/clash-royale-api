package com.example.clashroyaleapi.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 実際の messages*.properties を直接読んで検証する。Springコンテキストの起動は不要。
 */
class LabelResolverTest {

    private LabelResolver labels;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        // 実行環境の既定ロケールがja_JPでも英語要求が日本語に落ちないようにする(本番と同じ設定)。
        messageSource.setFallbackToSystemLocale(false);
        labels = new LabelResolver(messageSource);
    }

    @Test
    void 日本語ロケールではカード名を日本語に変換する() {
        assertEquals("ナイト", labels.cardName("Knight", Locale.JAPANESE));
    }

    @Test
    void キーに空白を含むカード名も引ける() {
        // .properties 側でエスケープしたキーが正しく読めているかの確認。
        assertEquals("ミニペッカ", labels.cardName("Mini P.E.K.K.A", Locale.JAPANESE));
        assertEquals("ベビードラゴン", labels.cardName("Baby Dragon", Locale.JAPANESE));
    }

    @Test
    void 誤訳修正済みのカード名が正しく引けることを確認する() {
        // 進捗ログ.md記載: 単純カタカナ音訳の誤訳を公式ローカライズ名に修正した経緯があるカード。
        assertEquals("オーブン", labels.cardName("Furnace", Locale.JAPANESE));
        assertEquals("60式ムート", labels.cardName("Cannon Cart", Locale.JAPANESE));
        assertEquals("アサシン ユーノ", labels.cardName("Bandit", Locale.JAPANESE));
    }

    @Test
    void 英語ロケールでは公式APIの英語名をそのまま使う() {
        assertEquals("Knight", labels.cardName("Knight", Locale.ENGLISH));
        assertEquals("Mini P.E.K.K.A", labels.cardName("Mini P.E.K.K.A", Locale.ENGLISH));
    }

    @Test
    void 対応表にない新カードは英語名にフォールバックする() {
        assertEquals("Unknown New Card", labels.cardName("Unknown New Card", Locale.JAPANESE));
    }

    @Test
    void ゲームモードは日本語ロケールでのみ英語名を併記する() {
        assertEquals("ランク戦 (Ladder)", labels.gameMode("Ladder", Locale.JAPANESE));
        assertEquals("Ladder", labels.gameMode("Ladder", Locale.ENGLISH));
        assertEquals("SomeFutureMode", labels.gameMode("SomeFutureMode", Locale.JAPANESE));
    }

    @Test
    void 役職はロケールごとの表記に解決する() {
        assertEquals("サブリーダー", labels.role("coLeader", Locale.JAPANESE));
        assertEquals("Co-leader", labels.role("coLeader", Locale.ENGLISH));
        // 未整備の言語はデフォルト(英語)にフォールバックする。
        assertEquals("Co-leader", labels.role("coLeader", Locale.FRENCH));
    }

    @Test
    void 対応表にない役職はAPIの生値をそのまま返す() {
        assertEquals("futureRole", labels.role("futureRole", Locale.JAPANESE));
    }

    @Test
    void nullや空文字はハイフンを返す() {
        assertEquals("-", labels.cardName(null, Locale.JAPANESE));
        assertEquals("-", labels.cardName("   ", Locale.JAPANESE));
        assertEquals("-", labels.gameMode(null, Locale.JAPANESE));
        assertEquals("-", labels.role("", Locale.JAPANESE));
    }
}
