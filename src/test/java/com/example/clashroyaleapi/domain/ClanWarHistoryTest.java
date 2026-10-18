package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.dto.RiverRaceLogResponse;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClanWarHistoryTest {

    private static RiverRaceLogResponse.Standing standing(String tag, int rank, int fame, int trophyChange) {
        return new RiverRaceLogResponse.Standing(rank, trophyChange,
                new RiverRaceLogResponse.Clan(tag, "clan", fame, List.of()));
    }

    private static RiverRaceLogResponse.RiverRace race(String createdDate, RiverRaceLogResponse.Standing... standings) {
        return new RiverRaceLogResponse.RiverRace(createdDate, Arrays.asList(standings));
    }

    @Test
    void 週ごとに自分のクランの順位とメダルとトロフィーの増減を新しい順に取り出す() {
        ClanWarHistory history = ClanWarHistory.of("#MINE", new RiverRaceLogResponse(List.of(
                race("20261012T094017.000Z", standing("#OTHER", 1, 10000, 20), standing("#MINE", 2, 8000, 10)),
                race("20261005T094004.000Z", standing("#MINE", 1, 23200, 100), standing("#OTHER", 2, 9000, 50)))));

        assertEquals(List.of(
                        new ClanWarHistory.Week(LocalDate.of(2026, 10, 12), 2, 8000, 10),
                        new ClanWarHistory.Week(LocalDate.of(2026, 10, 5), 1, 23200, 100)),
                history.weeks());
    }

    @Test
    void メダル0の週は同点の中の並びに意味が無いので順位を出さない() {
        ClanWarHistory history = ClanWarHistory.of("#MINE", new RiverRaceLogResponse(List.of(
                race("20261005T093104.000Z", standing("#OTHER", 1, 600, 100), standing("#MINE", 2, 0, -5)))));

        assertNull(history.weeks().get(0).rank());
        assertEquals(-5, history.weeks().get(0).trophyChange());
    }

    @Test
    void 一番新しい週から続く1位の数を連続として数える() {
        ClanWarHistory history = new ClanWarHistory(List.of(
                new ClanWarHistory.Week(LocalDate.of(2026, 10, 12), 1, 10000, 20),
                new ClanWarHistory.Week(LocalDate.of(2026, 10, 5), 1, 23200, 100),
                new ClanWarHistory.Week(LocalDate.of(2026, 9, 28), 3, 5000, -10),
                new ClanWarHistory.Week(LocalDate.of(2026, 9, 21), 1, 10000, 20)));

        assertEquals(3, history.firstPlaces());
        assertEquals(2, history.firstPlaceStreak());
    }

    @Test
    void 最新の週が1位でなければ連続は0() {
        ClanWarHistory history = new ClanWarHistory(List.of(
                new ClanWarHistory.Week(LocalDate.of(2026, 10, 12), null, 0, -4),
                new ClanWarHistory.Week(LocalDate.of(2026, 10, 5), 1, 10000, 20)));

        assertEquals(0, history.firstPlaceStreak());
    }

    @Test
    void タグの表記ゆれを吸収し自分のいない週と日時の読めない週は飛ばす() {
        ClanWarHistory history = ClanWarHistory.of("mine", new RiverRaceLogResponse(List.of(
                race("20261012T094017.000Z", standing("#MINE", 1, 10000, 20)),
                race("20261005T094004.000Z", standing("#OTHER", 1, 10000, 20)),
                race(null, standing("#MINE", 1, 10000, 20)),
                new RiverRaceLogResponse.RiverRace("20260928T094004.000Z", null))));

        assertEquals(1, history.weeks().size());
    }

    @Test
    void 履歴が無ければ空() {
        assertTrue(ClanWarHistory.of("#MINE", new RiverRaceLogResponse(null)).weeks().isEmpty());
        assertTrue(ClanWarHistory.of("#MINE", null).weeks().isEmpty());
    }
}
