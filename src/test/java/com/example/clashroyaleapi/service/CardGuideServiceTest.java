package com.example.clashroyaleapi.service;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 本物の解説ファイル(cards/guides)を読み、書き方の誤りで起動できなくならないことを確かめる。 */
class CardGuideServiceTest {

    private static final int HOG_RIDER = 26000021;
    private static final int ELECTRO_GIANT = 26000085;
    private static final int ELITE_BARBARIANS = 26000043;

    private final CardGuideService service = new CardGuideService();

    @Test
    void 書いたカードの日本語と英語の解説を読める() {
        for (int id : new int[] {HOG_RIDER, ELECTRO_GIANT, ELITE_BARBARIANS}) {
            assertTrue(service.guide(id, Locale.JAPANESE).isPresent(), "ja " + id);
            assertTrue(service.guide(id, Locale.ENGLISH).isPresent(), "en " + id);
        }
        assertEquals("ホグライダーの対策・使い方と使用率【クラロワ】",
                service.guide(HOG_RIDER, Locale.JAPANESE).orElseThrow().title());
    }

    @Test
    void 解説の無い言語では英語などを代わりに出さない() {
        assertTrue(service.guide(HOG_RIDER, Locale.of("es")).isEmpty());
        assertTrue(service.guide(HOG_RIDER, Locale.KOREAN).isEmpty());
    }

    @Test
    void 解説の無いカードには何も出さない() {
        assertTrue(service.guide(26000000, Locale.JAPANESE).isEmpty());
    }
}
