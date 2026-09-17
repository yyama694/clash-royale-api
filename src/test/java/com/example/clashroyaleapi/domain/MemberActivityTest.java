package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberActivityTest {

    // 2026-09-15T09:21:24Z を基準にする。
    private static final Instant NOW = Instant.parse("2026-09-15T09:21:24Z");

    @Test
    void 同日のアクセスは0日になる() {
        assertEquals(OptionalLong.of(0), MemberActivity.inactiveDays("20260915T092124.000Z", NOW));
    }

    @Test
    void 経過日数を切り捨てで数える() {
        // 23時間59分では1日に満たないので0日。
        assertEquals(OptionalLong.of(0), MemberActivity.inactiveDays("20260914T092225.000Z", NOW));
        assertEquals(OptionalLong.of(1), MemberActivity.inactiveDays("20260914T092124.000Z", NOW));
        assertEquals(OptionalLong.of(12), MemberActivity.inactiveDays("20260903T092124.000Z", NOW));
    }

    @Test
    void 長期離脱のメンバーも正しく数える() {
        // 実データで1113日のメンバーが存在した。
        assertEquals(OptionalLong.of(1113), MemberActivity.inactiveDays("20230829T092124.000Z", NOW));
    }

    @Test
    void 未来の日時でも負の日数にはしない() {
        // APIとサーバーの時刻がわずかにずれた場合の保険。
        assertEquals(OptionalLong.of(0), MemberActivity.inactiveDays("20260916T092124.000Z", NOW));
    }

    @Test
    void 七日以上アクセスが無いメンバーを長期離脱とみなす() {
        assertFalse(MemberActivity.isLongInactive(OptionalLong.of(6)));
        assertTrue(MemberActivity.isLongInactive(OptionalLong.of(7)));
        assertFalse(MemberActivity.isLongInactive(OptionalLong.empty()));
    }

    @Test
    void 解釈できない値は空を返す() {
        assertFalse(MemberActivity.inactiveDays(null, NOW).isPresent());
        assertFalse(MemberActivity.inactiveDays("", NOW).isPresent());
        assertFalse(MemberActivity.inactiveDays("2026-09-15T09:21:24Z", NOW).isPresent());
    }
}
