package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.dto.PlayerResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 所持カード全体のうち、何%を最大レベルにしているかをレアリティ別に集計する。
 * 「所持しているか」ではなく「最大レベルかどうか」だけを見る(PlayerResponse.OwnedCard参照)。
 */
public record CardCollection(List<RaritySummary> rarities, int totalCards, int maxedCards) {

    private static final List<String> RARITY_ORDER = List.of("common", "rare", "epic", "legendary", "champion");

    public record RaritySummary(String rarity, int totalCards, int maxedCards) {
    }

    public static CardCollection of(List<PlayerResponse.OwnedCard> cards) {
        Map<String, int[]> byRarity = new LinkedHashMap<>();
        for (PlayerResponse.OwnedCard card : cards) {
            int[] counts = byRarity.computeIfAbsent(card.rarity(), r -> new int[2]);
            counts[0]++;
            if (card.level() >= card.maxLevel()) {
                counts[1]++;
            }
        }
        List<String> order = byRarity.keySet().stream()
                .sorted(Comparator.comparingInt(CardCollection::rarityRank))
                .toList();

        List<RaritySummary> summaries = new ArrayList<>();
        int total = 0;
        int maxed = 0;
        for (String rarity : order) {
            int[] counts = byRarity.get(rarity);
            summaries.add(new RaritySummary(rarity, counts[0], counts[1]));
            total += counts[0];
            maxed += counts[1];
        }
        return new CardCollection(summaries, total, maxed);
    }

    private static int rarityRank(String rarity) {
        int index = RARITY_ORDER.indexOf(rarity);
        return index < 0 ? RARITY_ORDER.size() : index;
    }
}
