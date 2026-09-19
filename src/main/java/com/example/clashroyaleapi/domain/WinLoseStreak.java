package com.example.clashroyaleapi.domain;

import java.util.Optional;

/** 公式APIの currentWinLoseStreak(連勝なら正、連敗なら負)を、向きと回数に分ける。 */
public record WinLoseStreak(boolean winning, int count) {

    /** 0 と値なしは、連勝・連敗のどちらでもないので出さない。 */
    public static Optional<WinLoseStreak> of(Integer raw) {
        if (raw == null || raw == 0) {
            return Optional.empty();
        }
        return Optional.of(new WinLoseStreak(raw > 0, Math.abs(raw)));
    }
}
