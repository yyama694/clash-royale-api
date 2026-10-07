package com.example.clashroyaleapi.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

/**
 * ランク戦のシーズンの区切り。シーズンは毎月第1月曜の 09:00 UTC(日本時間18:00)に切り替わる。
 * 公式APIはシーズンの開始日時を返さないので、この決まりから計算する
 * (2026-10-05 09:00 UTC の切り替えを本番で観測し、RoyaleAPI のヘルプの説明とも一致)。
 */
public final class SeasonCalendar {

    private static final LocalTime SWITCH_TIME = LocalTime.of(9, 0);

    private SeasonCalendar() {
    }

    public static Instant currentSeasonStart(Instant now) {
        YearMonth month = YearMonth.from(now.atOffset(ZoneOffset.UTC));
        Instant start = startIn(month);
        return start.isAfter(now) ? startIn(month.minusMonths(1)) : start;
    }

    private static Instant startIn(YearMonth month) {
        return month.atDay(1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY))
                .atTime(SWITCH_TIME).toInstant(ZoneOffset.UTC);
    }
}
