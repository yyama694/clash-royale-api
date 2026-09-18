package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardLevelTest {

    /**
     * 実データ(`/player/2CLV2RP0/battles/20260916T195949.000Z`)のデッキ。
     * ランク戦はカードレベルが揃うため、レアリティが違っても同じ数字になるのが正しい。
     */
    @Test
    void レアリティが違っても同じ強さなら同じレベルになる() {
        assertEquals(11, CardLevel.inGame(11, 16)); // Skeletons(コモン)
        assertEquals(11, CardLevel.inGame(9, 14));  // Wizard(レア)
        assertEquals(11, CardLevel.inGame(6, 11));  // Lightning(エピック)
        assertEquals(11, CardLevel.inGame(3, 8));   // The Log(レジェンダリー)
        assertEquals(11, CardLevel.inGame(1, 6));   // Goblinstein(チャンピオン)
    }

    @Test
    void 上限まで育てたカードは上限のレベルになる() {
        assertEquals(16, CardLevel.inGame(16, 16));
        assertEquals(16, CardLevel.inGame(6, 6));
    }

    @Test
    void 想定外のmaxLevelは生値のまま返す() {
        // 上限が引き上げられた場合や、APIがmaxLevelを返さなかった場合。
        assertEquals(9, CardLevel.inGame(9, 0));
        assertEquals(9, CardLevel.inGame(9, 17));
    }
}
