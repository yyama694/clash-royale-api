package com.example.clashroyaleapi.web.view;

import com.example.clashroyaleapi.domain.BattleResult;

import java.util.List;

/**
 * 対戦履歴一覧の1行。勝敗判定はテンプレートではなくここで確定させる。
 * 2v2では相手が2人になり味方もいるため、どちらもリストで持つ(1v1ではteammatesは空)。
 * selfCards は見ているプレイヤー本人がその対戦で使ったデッキ(公式APIが返さなければ空)。
 * selfScore・opponentScore はクラウン数で、デュエルでは取ったラウンドの数(roundScore が true)。
 */
public record BattleSummaryView(String battleTime, TimeView time, String gameMode, BattleResult result, int selfScore,
        int opponentScore, boolean roundScore, List<OpponentView> opponents, List<PlayerLinkView> teammates,
        TrophyChangeView trophyChange, List<CardView> selfCards) {
}
