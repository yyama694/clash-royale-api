package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrophyChangeTest {

    @Test
    void トロフィー戦はトロフィーでランク戦はレーティング() {
        assertEquals(new TrophyChange(TrophyChange.Kind.TROPHIES, 30), TrophyChange.of("PvP", 30).orElseThrow());
        assertEquals(new TrophyChange(TrophyChange.Kind.RATING, -21), TrophyChange.of("pathOfLegend", -21).orElseThrow());
    }

    @Test
    void イベント戦の値や増減の無い対戦は出さない() {
        assertTrue(TrophyChange.of("trail", 1).isEmpty());
        assertTrue(TrophyChange.of("riverRacePvP", null).isEmpty());
        assertTrue(TrophyChange.of("pathOfLegend", null).isEmpty());
        assertTrue(TrophyChange.of("PvP", 0).isEmpty());
        assertTrue(TrophyChange.of(null, 30).isEmpty());
    }
}
