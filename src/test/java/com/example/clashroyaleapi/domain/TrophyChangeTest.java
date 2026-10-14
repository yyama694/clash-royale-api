package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrophyChangeTest {

    @Test
    void トロフィー戦はトロフィーでランク戦はレーティング() {
        assertEquals(new TrophyChange(TrophyChange.Kind.TROPHIES, 30), TrophyChange.of("PvP", 30, null).orElseThrow());
        assertEquals(new TrophyChange(TrophyChange.Kind.RATING, -21), TrophyChange.of("pathOfLegend", -21, 7).orElseThrow());
    }

    @Test
    void イベント戦の値や増減の無い対戦は出さない() {
        assertTrue(TrophyChange.of("trail", 1, null).isEmpty());
        assertTrue(TrophyChange.of("riverRacePvP", null, null).isEmpty());
        assertTrue(TrophyChange.of("pathOfLegend", null, null).isEmpty());
        assertTrue(TrophyChange.of("PvP", 0, null).isEmpty());
        assertTrue(TrophyChange.of(null, 30, null).isEmpty());
    }

    @Test
    void ランク戦は天界ならレーティングでそれより下のリーグは出さない() {
        assertTrue(TrophyChange.of("pathOfLegend", 30, 3).isEmpty());
        assertTrue(TrophyChange.of("pathOfLegend", 30, 6).isEmpty());
        assertEquals(new TrophyChange(TrophyChange.Kind.RATING, 25), TrophyChange.of("pathOfLegend", 25, 7).orElseThrow());
        assertEquals(new TrophyChange(TrophyChange.Kind.RATING, 25), TrophyChange.of("pathOfLegend", 25, null).orElseThrow());
    }
}
