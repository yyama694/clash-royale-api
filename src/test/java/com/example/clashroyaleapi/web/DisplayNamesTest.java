package com.example.clashroyaleapi.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DisplayNamesTest {

    @Test
    void 名前に含まれる色指定タグを取り除く() {
        // 実データで確認した表記。
        assertEquals("Ale :D", DisplayNames.of("<c6>Ale :D"));
        assertEquals("XD ZEYAD", DisplayNames.of("XD <c2>ZEYAD"));
        assertEquals("Red name", DisplayNames.of("<c1>Red</c> name"));
    }

    @Test
    void タグを含まない名前はそのまま返す() {
        assertEquals("ダイナマイト四国", DisplayNames.of("ダイナマイト四国"));
        assertEquals("a<b>c", DisplayNames.of("a<b>c"));
    }

    @Test
    void タグだけの名前は空にせず元の値を返す() {
        assertEquals("<c2>", DisplayNames.of("<c2>"));
        assertNull(DisplayNames.of(null));
    }
}
