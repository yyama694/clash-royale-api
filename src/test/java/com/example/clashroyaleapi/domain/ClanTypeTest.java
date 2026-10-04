package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClanTypeTest {

    @Test
    void 公式APIの値からタイプを引く() {
        assertEquals(ClanType.INVITE_ONLY, ClanType.from("inviteOnly").orElseThrow());
        assertEquals("clan.type.inviteOnly", ClanType.INVITE_ONLY.messageKey());
        assertTrue(ClanType.from("public").isEmpty());
        assertTrue(ClanType.from(null).isEmpty());
    }

    @Test
    void 空きの人数は上限から数え_参加不可なら募集していないので0() {
        assertEquals(3, ClanType.OPEN.openSlots(47));
        assertEquals(1, ClanType.INVITE_ONLY.openSlots(49));
        assertEquals(0, ClanType.OPEN.openSlots(50));
        assertEquals(0, ClanType.CLOSED.openSlots(10));
    }
}
