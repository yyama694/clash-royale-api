package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.CurrentRiverRaceResponse;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClanWarParticipationTest {

    private static ClanResponse.Member member(String tag, String name) {
        return new ClanResponse.Member(tag, name, "member", 5000, 0, null);
    }

    private static CurrentRiverRaceResponse race(String periodType, CurrentRiverRaceResponse.Participant... participants) {
        return new CurrentRiverRaceResponse(periodType, new CurrentRiverRaceResponse.Clan("#CLAN", List.of(participants)));
    }

    private static CurrentRiverRaceResponse.Participant participant(String tag, int week, int today) {
        return new CurrentRiverRaceResponse.Participant(tag, "old name", week, today);
    }

    @Test
    void 今のメンバーだけを今日の使用数が少ない順に並べる() {
        ClanWarParticipation war = ClanWarParticipation.of(
                List.of(member("#A", "Alice"), member("#B", "Bob"), member("#C", "Carol")),
                race("warDay",
                        participant("#A", 8, 4),
                        participant("#B", 6, 2),
                        participant("#C", 3, 2),
                        // クランを抜けた元メンバーは出さない。
                        participant("#X", 12, 4)));

        assertEquals(List.of("#C", "#B", "#A"), war.members().stream().map(ClanWarParticipation.Member::tag).toList());
        assertEquals("Carol", war.members().get(0).name());
    }

    @Test
    void 参加者にいないメンバーは0として先頭に来る() {
        ClanWarParticipation war = ClanWarParticipation.of(
                List.of(member("#A", "Alice"), member("#NEW", "Newbie")),
                race("warDay", participant("#A", 4, 4)));

        ClanWarParticipation.Member first = war.members().get(0);
        assertEquals("#NEW", first.tag());
        assertEquals(0, first.decksUsedToday());
        assertEquals(0, first.decksUsedThisWeek());
    }

    @Test
    void 今日の集計はメンバーの人数と1日4デッキを上限にする() {
        ClanWarParticipation war = ClanWarParticipation.of(
                List.of(member("#A", "Alice"), member("#B", "Bob"), member("#C", "Carol")),
                race("warDay", participant("#A", 4, 4), participant("#B", 1, 1)));

        assertEquals(2, war.membersBattledToday());
        assertEquals(5, war.decksUsedToday());
        assertEquals(12, war.maxDecksToday());
    }

    @Test
    void タグの表記ゆれがあっても突き合わせられる() {
        ClanWarParticipation war = ClanWarParticipation.of(
                List.of(member("#abc", "Alice")), race("warDay", participant("#ABC", 4, 3)));

        assertEquals(3, war.members().get(0).decksUsedToday());
    }

    @Test
    void 攻撃できる日かどうかはperiodTypeで決まる() {
        List<ClanResponse.Member> members = List.of(member("#A", "Alice"));

        assertTrue(ClanWarParticipation.of(members, race("warDay")).battleDay());
        assertTrue(ClanWarParticipation.of(members, race("colosseum")).battleDay());
        assertFalse(ClanWarParticipation.of(members, race("training")).battleDay());
    }
}
