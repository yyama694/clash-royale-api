package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CardAbilityTest {

    @Test
    void コメントと空行を飛ばしてヒーローとチャンピオンを読み分ける() {
        List<CardAbility> abilities = CardAbility.parse(List.of("# comment", "", "Ice Golem\thero\t2",
                "Little Prince\tchampion\t3"));

        assertEquals(List.of(new CardAbility("Ice Golem", false, 2), new CardAbility("Little Prince", true, 3)),
                abilities);
    }

    @Test
    void 列の数や種類が違う行は読み込みで止める() {
        assertThrows(IllegalArgumentException.class, () -> CardAbility.parse(List.of("Ice Golem\thero")));
        assertThrows(IllegalArgumentException.class, () -> CardAbility.parse(List.of("Ice Golem\tevolution\t2")));
    }
}
