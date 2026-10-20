package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonCalendarTest {

    @Test
    void 今月の第1月曜9時UTCを過ぎていればその時刻が今のシーズンの開始() {
        assertEquals(Instant.parse("2026-10-05T09:00:00Z"),
                SeasonCalendar.currentSeasonStart(Instant.parse("2026-10-05T09:00:00Z")));
        assertEquals(Instant.parse("2026-10-05T09:00:00Z"),
                SeasonCalendar.currentSeasonStart(Instant.parse("2026-10-31T23:59:59Z")));
    }

    @Test
    void 今月の切り替え前なら前の月の第1月曜が今のシーズンの開始() {
        assertEquals(Instant.parse("2026-09-07T09:00:00Z"),
                SeasonCalendar.currentSeasonStart(Instant.parse("2026-10-05T08:59:59Z")));
        assertEquals(Instant.parse("2026-09-07T09:00:00Z"),
                SeasonCalendar.currentSeasonStart(Instant.parse("2026-10-01T00:00:00Z")));
    }

    @Test
    void 年をまたいでも前の月にさかのぼる() {
        assertEquals(Instant.parse("2026-12-07T09:00:00Z"),
                SeasonCalendar.currentSeasonStart(Instant.parse("2027-01-02T00:00:00Z")));
        assertEquals(Instant.parse("2027-01-04T09:00:00Z"),
                SeasonCalendar.currentSeasonStart(Instant.parse("2027-01-04T09:00:00Z")));
    }

    @Test
    void 直前に終わったシーズンは今のシーズンの開始の前の月() {
        assertEquals(YearMonth.of(2026, 9), SeasonCalendar.lastFinishedSeason(Instant.parse("2026-11-02T08:59:59Z")));
        assertEquals(YearMonth.of(2026, 10), SeasonCalendar.lastFinishedSeason(Instant.parse("2026-11-02T09:00:00Z")));
        assertEquals(YearMonth.of(2026, 12), SeasonCalendar.lastFinishedSeason(Instant.parse("2027-01-04T09:00:00Z")));
    }

    @Test
    void 切り替え直後に一覧へ終わったばかりのシーズンが無ければ待っている扱い() {
        Instant justAfter = Instant.parse("2026-11-02T09:01:00Z");

        assertTrue(SeasonCalendar.awaitingLastFinishedSeason(List.of("2026-08", "2026-09"), justAfter));
        assertFalse(SeasonCalendar.awaitingLastFinishedSeason(List.of("2026-09", "2026-10"), justAfter));
        assertFalse(SeasonCalendar.awaitingLastFinishedSeason(List.of("2026-08", "2026-09"),
                Instant.parse("2026-11-02T08:59:00Z")));
        assertFalse(SeasonCalendar.awaitingLastFinishedSeason(List.of("2026-08", "2026-09"),
                Instant.parse("2026-11-05T09:00:00Z")));
    }
}
