package com.example.clashroyaleapi.domain;

import java.time.DayOfWeek;
import java.time.Duration;
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

    /**
     * シーズン開始直後として扱う期間(2026-10-07にユーザーが決定)。この間だけ、世界のランキングが1000人に満たなければ
     * ランキング画面で「始まったばかり」と断り、デッキ集計は前のシーズンの最終順位で行う。
     * 1000人に届かないシーズンが来ても、その扱いがシーズン中ずっと続かないようにするため。
     */
    public static final Duration NEW_SEASON_PERIOD = Duration.ofDays(3);

    private SeasonCalendar() {
    }

    public static Instant currentSeasonStart(Instant now) {
        YearMonth month = YearMonth.from(now.atOffset(ZoneOffset.UTC));
        Instant start = startIn(month);
        return start.isAfter(now) ? startIn(month.minusMonths(1)) : start;
    }

    public static Instant newSeasonPeriodEnd(Instant now) {
        return currentSeasonStart(now).plus(NEW_SEASON_PERIOD);
    }

    public static boolean inNewSeasonPeriod(Instant now) {
        return now.isBefore(newSeasonPeriodEnd(now));
    }

    private static Instant startIn(YearMonth month) {
        return month.atDay(1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY))
                .atTime(SWITCH_TIME).toInstant(ZoneOffset.UTC);
    }
}
