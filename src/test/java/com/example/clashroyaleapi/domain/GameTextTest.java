package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GameTextTest {

    @Test
    void 実データにある形の色タグを取り除く() {
        // 2026-09-17に公式APIの個人ランキング・対戦履歴で実際に見つかった名前。
        assertEquals("XD ZEYAD", GameText.stripFormatting("XD <c2>ZEYAD"));
        assertEquals("Liicht", GameText.stripFormatting("<c2>Lii<c5>cht"));
        assertEquals("Green Kush", GameText.stripFormatting("<c3> Green Kush"));
        assertEquals("子誠大大", GameText.stripFormatting("<c8>子<c2>誠大大"));
    }

    @Test
    void 色の16進指定と閉じタグも取り除く() {
        assertEquals("Red name", GameText.stripFormatting("<cff0000>Red</c> name"));
    }

    @Test
    void タグでない山括弧はそのまま残す() {
        assertEquals("<cool> guy", GameText.stripFormatting("<cool> guy"));
        assertEquals("a < b", GameText.stripFormatting("a < b"));
    }

    @Test
    void タグしか無い名前は元の値のまま() {
        assertEquals("<c2>", GameText.stripFormatting("<c2>"));
    }

    @Test
    void nullはnullのまま() {
        assertNull(GameText.stripFormatting(null));
    }
}
