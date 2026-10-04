package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.config.IcuMessageSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        assertEquals("ミニP.E.K.K.A", labels.cardName("Mini P.E.K.K.A", Locale.JAPANESE));
        assertEquals("ベビードラゴン", labels.cardName("Baby Dragon", Locale.JAPANESE));
    }

    @Test
    void 誤訳修正済みのカード名が正しく引けることを確認する() {
        // 進捗ログ.md記載: 単純カタカナ音訳の誤訳や通称を、ゲーム内の公式表記に直した経緯があるカード。
        assertEquals("オーブン", labels.cardName("Furnace", Locale.JAPANESE));
        assertEquals("60式 ムート", labels.cardName("Cannon Cart", Locale.JAPANESE));
        assertEquals("アサシン ユーノ", labels.cardName("Bandit", Locale.JAPANESE));
        assertEquals("P.E.K.K.A", labels.cardName("P.E.K.K.A", Locale.JAPANESE));
    }

    @Test
    void 並べ替えのキーは漢字で始まる日本語名だけ読み仮名にする() {
        assertEquals("タイホウ", labels.cardSortKey("Cannon", Locale.JAPANESE));
        assertEquals("ナイト", labels.cardSortKey("Knight", Locale.JAPANESE));
        assertEquals("Cannon", labels.cardSortKey("Cannon", Locale.ENGLISH));
    }

    @Test
    void 通称は定着している言語にだけあり無ければnull() {
        assertEquals("ペッカ", labels.cardAlias("P.E.K.K.A", Locale.JAPANESE));
        assertEquals("ミニペッカ", labels.cardAlias("Mini P.E.K.K.A", Locale.JAPANESE));
        assertNull(labels.cardAlias("P.E.K.K.A", Locale.ENGLISH));
        assertNull(labels.cardAlias("Knight", Locale.JAPANESE));
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
        // Challenge_* は type が trail のため、接頭辞を見ないと「イベント」に埋もれる。
        assertEquals("チャレンジ", labels.gameMode("trail", "Challenge_AllCards_EventDeck_NoSet", Locale.JAPANESE));
        assertEquals("Challenge", labels.gameMode("trail", "Challenge_AllCards_EventDeck_NoSet", Locale.ENGLISH));
        // RR_* は type が unknown の特殊ルールのイベント戦(2026-10-01に200人の対戦履歴で確認)。
        assertEquals("イベント", labels.gameMode("unknown", "RR_Snowball_bombardment", Locale.JAPANESE));
    }

    @Test
    void 名前の辞書に無いtrailはトロフィー戦ではなくイベント戦() {
        // トロフィー戦(Ladder)と協力バトル(TeamVsTeam)は名前で解決される。残るのはメガドラフトなどのイベント戦で、
        // 以前は「通常バトル」と出ていた。
        assertEquals("イベント", labels.gameMode("trail", "Chaos_1v1_MegaDraft_All", Locale.JAPANESE));
        assertEquals("Event", labels.gameMode("trail", "Chaos_1v1_MegaDraft_All", Locale.ENGLISH));
        assertEquals("1対1エンタメ", labels.gameMode("trail", "Showdown_Friendly", Locale.JAPANESE));
    }

    @Test
    void 人気順位の分母は使われていたカードの数だと分かるように書く() {
        // 分母はトッププレイヤーが1人以上使っていたカードの数で、ゲームの全カード数ではない(2026-09-30のファクトチェック)。
        // 複数形(plural)を使うので、本番と同じ ICU のメッセージソースで組み立てる。
        LabelResolver icu = new LabelResolver(IcuMessageSource.forBasename("messages"));
        assertEquals("使われていた120枚中118位", icu.message("cardUsage.rank.value", Locale.JAPANESE, 118, 120));
        assertEquals("#118 of 120 cards used", icu.message("cardUsage.rank.value", Locale.ENGLISH, 118, 120));
        assertEquals("118-е из 121 использованной карты",
                icu.message("cardUsage.rank.value", Locale.forLanguageTag("ru"), 118, 121));
        assertEquals("118-е из 120 использованных карт",
                icu.message("cardUsage.rank.value", Locale.forLanguageTag("ru"), 118, 120));
    }

    @Test
    void 辞書に無い特殊ルールのフレンドバトルとトーナメントは種別で解決する() {
        assertEquals("フレンドバトル", labels.gameMode("friendly", "Chaos_1v1_Draft", Locale.JAPANESE));
        assertEquals("フレンドバトル", labels.gameMode("clanMate", "Crazy_Arena_SuddenDeath", Locale.JAPANESE));
        assertEquals("フレンドバトル", labels.gameMode("clanMate2v2", "TeamVsTeam_FutureMode", Locale.JAPANESE));
        assertEquals("トーナメント", labels.gameMode("tournament", "Chaos_1v1_MegaDraft_All", Locale.JAPANESE));
    }

    @Test
    void 種別がunknownでもゲームデータにあるモード名は公式の表記で出す() {
        assertEquals("トリプルドラフト", labels.gameMode("unknown", "Draft_Competitive", Locale.JAPANESE));
        assertEquals("メガドラフトチャレンジ", labels.gameMode("unknown", "PickMode", Locale.JAPANESE));
        assertEquals("訓練キャンプ", labels.gameMode("PvE", "Training", Locale.JAPANESE));
        assertEquals("Mega-Auswahlherausforderung", labels.gameMode("unknown", "PickMode", Locale.GERMAN));
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
        assertEquals("공동 대표", labels.role("coLeader", Locale.KOREAN));
        assertEquals("Lider Yardımcısı", labels.role("coLeader", Locale.forLanguageTag("tr")));
        // 未整備の言語はデフォルト(英語)にフォールバックする。
        assertEquals("Co-leader", labels.role("coLeader", Locale.forLanguageTag("pl")));
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
