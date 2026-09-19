package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.dto.PlayerResponse;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardCollectionTest {

    @Test
    void レアリティ別に最大レベルの枚数を集計する() {
        CardCollection collection = CardCollection.of(List.of(
                card("Knight", 16, 16, "common"),
                card("Skeletons", 10, 16, "common"),
                card("Valkyrie", 14, 14, "rare")));

        assertEquals(3, collection.totalCards());
        assertEquals(2, collection.maxedCards());
        assertEquals(
                List.of(new CardCollection.RaritySummary("common", 2, 1),
                        new CardCollection.RaritySummary("rare", 1, 1)),
                collection.rarities());
    }

    /**
     * 実データ(2026-09-19、公式APIから実測)で確認した挙動: countが0でもlevel==maxLevelのカードがある
     * (手持ちを使い切って最大レベルにした場合)。countは見ずlevel/maxLevelだけで最大レベルを判定する。
     */
    @Test
    void countが0でも最大レベルなら最大レベルとして数える() {
        PlayerResponse.OwnedCard maxedWithNoSpares = new PlayerResponse.OwnedCard(1, "Mega Knight", 8, 8, 0,
                "legendary");

        CardCollection collection = CardCollection.of(List.of(maxedWithNoSpares));

        assertEquals(1, collection.maxedCards());
    }

    @Test
    void レアリティはゲーム内の順で並ぶ() {
        CardCollection collection = CardCollection.of(List.of(
                card("Mega Knight", 1, 8, "legendary"),
                card("Golden Knight", 1, 6, "champion"),
                card("Knight", 1, 16, "common"),
                card("Fireball", 1, 11, "epic"),
                card("Valkyrie", 1, 14, "rare")));

        assertEquals(List.of("common", "rare", "epic", "legendary", "champion"),
                collection.rarities().stream().map(CardCollection.RaritySummary::rarity).toList());
    }

    private static PlayerResponse.OwnedCard card(String name, int level, int maxLevel, String rarity) {
        return new PlayerResponse.OwnedCard(1, name, level, maxLevel, 1, rarity);
    }
}
