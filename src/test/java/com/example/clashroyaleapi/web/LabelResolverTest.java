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
    void ゲームモードは辞書にあれば名前で解決する() {
        assertEquals("トロフィー目標", labels.gameMode("trail", "Ladder", Locale.JAPANESE));
        assertEquals("Trophy Road", labels.gameMode("trail", "Ladder", Locale.ENGLISH));
    }

    @Test
    void 辞書にない名前は対戦種別で解決する() {
        // gameMode.name はアリーナやイベントごとに増える(Ranked1v1_NewArena2 など)ため、名前の完全一致では追いつかない。
        assertEquals("ランク戦", labels.gameMode("pathOfLegend", "Ranked1v1_NewArena2", Locale.JAPANESE));
        assertEquals("Rank Battle", labels.gameMode("pathOfLegend", "Ranked1v1_NewArena2", Locale.ENGLISH));
    }

    @Test
    void イベント名は接頭辞で分類する() {
        // Challenge_* は type が trail のため、接頭辞を見ないと「通常バトル」に埋もれる。
        assertEquals("チャレンジ", labels.gameMode("trail", "Challenge_AllCards_EventDeck_NoSet", Locale.JAPANESE));
        assertEquals("Challenge", labels.gameMode("trail", "Challenge_AllCards_EventDeck_NoSet", Locale.ENGLISH));
    }

    @Test
    void 名前も種別も辞書に無ければその他にし内部IDは出さない() {
        assertEquals("その他", labels.gameMode("futureType", "SomeFutureMode", Locale.JAPANESE));
        assertEquals("Other", labels.gameMode("futureType", "SomeFutureMode", Locale.ENGLISH));
    }

    @Test
    void 役職はロケールごとの表記に解決する() {
        assertEquals("サブリーダー", labels.role("coLeader", Locale.JAPANESE));
        assertEquals("Co-leader", labels.role("coLeader", Locale.ENGLISH));
        // 未整備の言語はデフォルト(英語)にフォールバックする。
        assertEquals("Co-leader", labels.role("coLeader", Locale.KOREAN));
    }

    @Test
    void 対応表にない役職はAPIの生値をそのまま返す() {
        assertEquals("futureRole", labels.role("futureRole", Locale.JAPANESE));
    }

    @Test
    void nullや空文字はハイフンを返す() {
        assertEquals("-", labels.cardName(null, Locale.JAPANESE));
        assertEquals("-", labels.cardName("   ", Locale.JAPANESE));
        assertEquals("-", labels.gameMode(null, null, Locale.JAPANESE));
        assertEquals("-", labels.role("", Locale.JAPANESE));
    }
}
