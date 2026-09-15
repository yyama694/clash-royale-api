package com.example.clashroyaleapi.web;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoleLabelsTest {

    @Test
    void 日本語ロケールではゲーム内表記の日本語ラベルを返す() {
        assertEquals("リーダー", RoleLabels.label("leader", Locale.JAPANESE));
        assertEquals("サブリーダー", RoleLabels.label("coLeader", Locale.JAPANESE));
        assertEquals("長老", RoleLabels.label("elder", Locale.JAPANESE));
        assertEquals("メンバー", RoleLabels.label("member", Locale.JAPANESE));
    }

    @Test
    void 英語ロケールでは正式な英語表記を返す() {
        assertEquals("Co-leader", RoleLabels.label("coLeader", Locale.ENGLISH));
    }

    @Test
    void 未整備の言語は英語表記にフォールバックする() {
        assertEquals("Co-leader", RoleLabels.label("coLeader", Locale.FRENCH));
    }

    @Test
    void ロケールがnullでも英語表記にフォールバックする() {
        assertEquals("Co-leader", RoleLabels.label("coLeader", null));
    }

    @Test
    void 対応表にない役職はAPIの生値をそのまま返す() {
        assertEquals("futureRole", RoleLabels.label("futureRole", Locale.JAPANESE));
    }

    @Test
    void nullや空文字はハイフンを返す() {
        assertEquals("-", RoleLabels.label(null, Locale.JAPANESE));
        assertEquals("-", RoleLabels.label("", Locale.JAPANESE));
    }
}
