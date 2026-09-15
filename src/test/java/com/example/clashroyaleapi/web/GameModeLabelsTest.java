package com.example.clashroyaleapi.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameModeLabelsTest {

    @Test
    void 既知のモードは日本語ラベルと英語名を併記する() {
        assertEquals("ランク戦 (Ladder)", GameModeLabels.label("Ladder"));
    }

    @Test
    void 未知のモードは英語名をそのまま返す() {
        assertEquals("SomeFutureMode", GameModeLabels.label("SomeFutureMode"));
    }

    @Test
    void nullや空文字はハイフンを返す() {
        assertEquals("-", GameModeLabels.label(null));
        assertEquals("-", GameModeLabels.label(""));
    }
}
