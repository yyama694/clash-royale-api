package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardFormTest {

    @Test
    void 対戦のevolutionLevelは1が進化で2がヒーロー() {
        assertEquals(CardForm.NORMAL, CardForm.ofBattle(null));
        assertEquals(CardForm.EVOLUTION, CardForm.ofBattle(1));
        assertEquals(CardForm.HERO, CardForm.ofBattle(2));
    }

    @Test
    void 対戦では付かない値は通常として扱う() {
        assertEquals(CardForm.NORMAL, CardForm.ofBattle(0));
        assertEquals(CardForm.NORMAL, CardForm.ofBattle(3));
    }

    @Test
    void 集計ファイルの値から元の形に戻せる() {
        for (CardForm form : CardForm.values()) {
            assertEquals(form, CardForm.ofBattle(form.battleLevel()));
        }
    }
}
