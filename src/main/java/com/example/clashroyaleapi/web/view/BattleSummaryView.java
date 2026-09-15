package com.example.clashroyaleapi.web.view;

import com.example.clashroyaleapi.domain.BattleResult;

/** 対戦履歴一覧の1行。勝敗判定はテンプレートではなくここで確定させる。 */
public record BattleSummaryView(String battleTime, String gameMode, BattleResult result, int selfCrowns,
        int opponentCrowns, String opponentName, String opponentPathTag) {
}
