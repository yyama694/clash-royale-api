package com.example.clashroyaleapi.web;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardNameLabelsTest {

    @Test
    void 日本語以外のロケールでは英語名をそのまま返す() {
        assertEquals("Knight", CardNameLabels.label("Knight", Locale.ENGLISH));
    }

    @Test
    void 日本語ロケールかつ対応表にあるカードは日本語名を返す() {
        assertEquals("ナイト", CardNameLabels.label("Knight", Locale.JAPANESE));
    }

    @Test
    void 誤訳修正済みのカード名が正しく引けることを確認する() {
        // 進捗ログ.md記載: 単純カタカナ音訳の誤訳を公式ローカライズ名に修正した経緯があるカード。
        assertEquals("オーブン", CardNameLabels.label("Furnace", Locale.JAPANESE));
        assertEquals("60式ムート", CardNameLabels.label("Cannon Cart", Locale.JAPANESE));
        assertEquals("アサシン ユーノ", CardNameLabels.label("Bandit", Locale.JAPANESE));
    }

    @Test
    void 日本語ロケールでも対応表にないカードは英語名にフォールバックする() {
        assertEquals("Unknown New Card", CardNameLabels.label("Unknown New Card", Locale.JAPANESE));
    }

    @Test
    void ロケールがnullの場合は英語名をそのまま返す() {
        assertEquals("Knight", CardNameLabels.label("Knight", null));
    }

    @Test
    void nullや空文字はハイフンを返す() {
        assertEquals("-", CardNameLabels.label(null, Locale.JAPANESE));
        assertEquals("-", CardNameLabels.label("", Locale.JAPANESE));
        assertEquals("-", CardNameLabels.label("   ", Locale.JAPANESE));
    }
}
