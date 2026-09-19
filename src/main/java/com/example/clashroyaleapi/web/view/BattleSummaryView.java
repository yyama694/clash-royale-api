package com.example.clashroyaleapi.web.view;

import com.example.clashroyaleapi.domain.BattleResult;

import java.util.List;

/**
 * 対戦履歴一覧の1行。勝敗判定はテンプレートではなくここで確定させる。
 * 2v2では相手が2人になり味方もいるため、どちらもリストで持つ(1v1ではteammatesは空)。
 */
public record BattleSummaryView(String battleTime, TimeView time, String gameMode, BattleResult result, int selfCrowns,
        int opponentCrowns, List<OpponentView> opponents, List<PlayerLinkView> teammates) {
}
