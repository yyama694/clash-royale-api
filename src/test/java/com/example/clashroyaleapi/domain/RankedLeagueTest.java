package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankedLeagueTest {

    @Test
    void 今の7段階の番号だけを受け付ける() {
        assertEquals(OptionalInt.of(1), RankedLeague.of(1));
        assertEquals(OptionalInt.of(7), RankedLeague.of(7));
        assertEquals(OptionalInt.empty(), RankedLeague.of(0));
        assertEquals(OptionalInt.empty(), RankedLeague.of(8));
        assertEquals(OptionalInt.empty(), RankedLeague.of(null));
    }

    @Test
    void 今シーズンのマスターIはランク戦に入れるトロフィーの人だけ() {
        assertEquals(OptionalInt.of(1), RankedLeague.currentOf(1, 14000));
        assertEquals(OptionalInt.empty(), RankedLeague.currentOf(1, 13999));
        assertEquals(OptionalInt.of(2), RankedLeague.currentOf(2, 9000));
    }
}
