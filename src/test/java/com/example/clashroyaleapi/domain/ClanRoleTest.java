package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClanRoleTest {

    @Test
    void 公式APIの値から役職を引く() {
        assertEquals(ClanRole.CO_LEADER, ClanRole.from("coLeader").orElseThrow());
        assertEquals("role.coLeader", ClanRole.CO_LEADER.messageKey());
    }

    @Test
    void 公式APIの値は大文字小文字を区別する() {
        assertTrue(ClanRole.from("coleader").isEmpty());
        assertTrue(ClanRole.from(null).isEmpty());
    }

    @Test
    void 序列はリーダーが最上位で未知の役職は最後() {
        assertTrue(ClanRole.rankOf("leader") < ClanRole.rankOf("coLeader"));
        assertTrue(ClanRole.rankOf("coLeader") < ClanRole.rankOf("elder"));
        assertTrue(ClanRole.rankOf("elder") < ClanRole.rankOf("member"));
        assertEquals(ClanRole.UNKNOWN_RANK, ClanRole.rankOf("admin"));
        assertEquals(ClanRole.UNKNOWN_RANK, ClanRole.rankOf(null));
    }
}
