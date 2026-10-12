package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankChangeTest {

    @Test
    void 前の順位との差を上がった下がった変わらないに分ける() {
        assertEquals(new RankChange(RankChange.Kind.UP, 3), RankChange.of(2, 5));
        assertEquals(new RankChange(RankChange.Kind.DOWN, 4), RankChange.of(10, 6));
        assertEquals(new RankChange(RankChange.Kind.SAME, 0), RankChange.of(7, 7));
    }

    @Test
    void 比べる記録に居なかった人は圏外から入ったとする() {
        assertEquals(new RankChange(RankChange.Kind.NEW, 0), RankChange.of(999, null));
    }
}
