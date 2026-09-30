package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;

import java.util.List;
import java.util.Optional;

/**
 * 直近の対戦履歴の、新しい方から続いている連勝・連敗。数える対戦は戦績サマリーと同じ(BattleExclusion を除く)。
 * 公式APIの currentWinLoseStreak は使わない。通常のトロフィー戦だけを数えているとみられ、ランク戦中心の上位勢では
 * 何か月も前の値が残っていた(2026-09-29、116人中102人が対戦履歴と不一致。世界1位が「258連勝中」と出ていた)。
 *
 * @param atLeast 取得できた対戦がすべて同じ結果だった。それより前は分からないため「N連勝以上」と出す
 */
public record WinLoseStreak(boolean winning, int count, boolean atLeast) {

    /** API は対戦履歴を新しい順に返す(2026-10-01に50人分で確認)。 */
    public static Optional<WinLoseStreak> from(List<BattleLogEntry> battleLog) {
        List<BattleResult> results = battleLog.stream()
                .filter(BattleResult::hasBothSides)
                .filter(battle -> BattleExclusion.of(battle).isEmpty())
                .map(BattleResult::of)
                .toList();
        // 引き分けは連勝も連敗も途切れさせる。
        if (results.isEmpty() || results.getFirst() == BattleResult.DRAW) {
            return Optional.empty();
        }
        BattleResult latest = results.getFirst();
        int count = 0;
        while (count < results.size() && results.get(count) == latest) {
            count++;
        }
        return Optional.of(new WinLoseStreak(latest == BattleResult.WIN, count, count == results.size()));
    }

    /** メッセージキーの末尾に使う表記(win / lose / winAtLeast / loseAtLeast)。 */
    public String code() {
        return (winning ? "win" : "lose") + (atLeast ? "AtLeast" : "");
    }
}
