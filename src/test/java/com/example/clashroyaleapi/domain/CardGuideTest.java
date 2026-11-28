package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardGuideTest {

    @Test
    void 見出しの行と小見出しごとの段落と箇条書きを読む() {
        CardGuide guide = CardGuide.parse(List.of(
                "# コメント",
                "title: タイトル",
                "heading: 見出し",
                "note: 断り書き: コロンを含む",
                "",
                "## どんなカード?",
                "段落1",
                "段落2",
                "",
                "## 対策",
                "- 項目1",
                "- 項目2",
                "あとの段落",
                "- 項目3"));

        assertEquals("タイトル", guide.title());
        assertEquals("見出し", guide.heading());
        assertEquals("断り書き: コロンを含む", guide.note());
        assertEquals(2, guide.sections().size());

        CardGuide.Section first = guide.sections().get(0);
        assertEquals("どんなカード?", first.heading());
        assertEquals(List.of("段落1", "段落2"), first.blocks().stream().map(CardGuide.Block::text).toList());
        assertFalse(first.blocks().get(0).list());

        CardGuide.Section second = guide.sections().get(1);
        assertEquals(3, second.blocks().size());
        assertTrue(second.blocks().get(0).list());
        assertNull(second.blocks().get(0).text());
        assertEquals(List.of("項目1", "項目2"), second.blocks().get(0).items());
        assertEquals("あとの段落", second.blocks().get(1).text());
        assertEquals(List.of("項目3"), second.blocks().get(2).items());
    }

    @Test
    void 書き方が違えば読まずに知らせる() {
        assertThrows(IllegalArgumentException.class, () -> CardGuide.parse(List.of(
                "title: t", "heading: h", "note: n", "unknown: x", "## 見出し", "本文")));
        assertThrows(IllegalArgumentException.class, () -> CardGuide.parse(List.of(
                "heading: h", "note: n", "## 見出し", "本文")));
        assertThrows(IllegalArgumentException.class, () -> CardGuide.parse(List.of(
                "title: t", "heading: h", "note: n")));
        assertThrows(IllegalArgumentException.class, () -> CardGuide.parse(List.of(
                "title: t", "heading: h", "note: n", "## 空の見出し", "## 見出し", "本文")));
    }
}
