package com.example.clashroyaleapi.web.view;

import java.util.List;

public record CardCollectionView(int totalCards, int maxedCards, int maxedPercent, List<RaritySummaryView> rarities) {

    public record RaritySummaryView(String rarityLabel, int totalCards, int maxedCards, int maxedPercent) {
    }
}
