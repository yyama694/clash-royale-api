package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BattleResultTest {

    @Test
    void クラウン数の比較で勝敗を決める() {
        assertEquals(BattleResult.WIN, BattleResult.of(3, 1));
        assertEquals(BattleResult.LOSE, BattleResult.of(0, 1));
        assertEquals(BattleResult.DRAW, BattleResult.of(1, 1));
    }

    @Test
    void codeはメッセージキーとCSSクラスに使う小文字表記() {
        assertEquals("win", BattleResult.WIN.code());
        assertEquals("lose", BattleResult.LOSE.code());
        assertEquals("draw", BattleResult.DRAW.code());
    }
}
