package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WinLoseStreakTest {

    @Test
    void 正の値は連勝() {
        assertEquals(new WinLoseStreak(true, 3), WinLoseStreak.of(3).orElseThrow());
    }

    @Test
    void 負の値は連敗で回数は絶対値() {
        assertEquals(new WinLoseStreak(false, 3), WinLoseStreak.of(-3).orElseThrow());
    }

    @Test
    void ゼロと値なしは出さない() {
        assertTrue(WinLoseStreak.of(0).isEmpty());
        assertTrue(WinLoseStreak.of(null).isEmpty());
    }
}
