package com.example.clashroyaleapi.web.view;

import java.util.List;

public record BattleStatsView(int total, int wins, int losses, int draws, List<CardPerformanceView> favoriteCards,
        List<CardPerformanceView> weakCards) {

    public boolean hasCardRanking() {
        return !favoriteCards.isEmpty() || !weakCards.isEmpty();
    }
}
