package com.example.clashroyaleapi.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagsTest {

    @Test
    void 先頭のシャープを補って大文字に揃える() {
        assertEquals("#2ABC123", Tags.normalize("2abc123"));
        assertEquals("#2ABC123", Tags.normalize("#2ABC123"));
    }

    @Test
    void コピペで混入した前後の空白を取り除く() {
        assertEquals("#2ABC123", Tags.normalize("  #2ABC123 "));
    }

    @Test
    void クラロワのタグに存在しない英字Oは数字のゼロとして扱う() {
        assertEquals("#2Y0LQ", Tags.normalize("2YOLQ"));
    }

    @Test
    void パスに載せる際はシャープを除いた表記を返す() {
        assertEquals("2ABC123", Tags.toPathSegment("#2abc123"));
    }

    @Test
    void 英数字のみならタグとみなす() {
        assertTrue(Tags.looksLikeTag("2ABC123"));
        assertTrue(Tags.looksLikeTag("#2abc123"));
    }

    @Test
    void 日本語のクラン名や短すぎる文字列はタグとみなさない() {
        // タグ検索を空振りさせずに、いきなりクラン名検索へ回すための判定。
        assertFalse(Tags.looksLikeTag("償い"));
        assertFalse(Tags.looksLikeTag("クラロワ最強クラン"));
        assertFalse(Tags.looksLikeTag("AB"));
        assertFalse(Tags.looksLikeTag(""));
    }

    @Test
    void nullでも例外にならない() {
        assertEquals("#", Tags.normalize(null));
        assertFalse(Tags.looksLikeTag(null));
    }
}
