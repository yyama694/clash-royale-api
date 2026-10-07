package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
