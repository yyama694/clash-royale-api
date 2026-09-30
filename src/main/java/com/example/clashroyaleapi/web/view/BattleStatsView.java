package com.example.clashroyaleapi.web.view;

import java.util.List;

/** cardRanking は得意/苦手を選べるだけ対戦したカードがあったか(PlayerBattleStats 参照)。 */
public record BattleStatsView(int total, int wins, int losses, int draws, int friendlyExcluded,
        int boatDefenseExcluded, boolean cardRanking, List<CardPerformanceView> favoriteCards,
        List<CardPerformanceView> weakCards) {
}
