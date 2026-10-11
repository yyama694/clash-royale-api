package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.client.exception.CardNotFoundException;
import com.example.clashroyaleapi.domain.CardUsage;
import com.example.clashroyaleapi.domain.TopDecks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CardServiceTest {

    private CardService cardService;

    @BeforeEach
    void setUp() {
        ClashRoyaleApiClient apiClient = mock(ClashRoyaleApiClient.class);
        when(apiClient.getCards()).thenReturn(new CardsResponse(
                List.of(
                        card(1, "Epic5", "epic", 5),
                        card(2, "Mirror", "epic", null),
                        card(3, "Common3", "common", 3),
                        card(4, "Epic2", "epic", 2),
                        card(5, "Common1", "common", 1)),
                List.of(card(9, "Tower Princess", "common", null))));
        cardService = new CardService(apiClient);
    }

    @Test
    void 一覧はレアリティ順に分けエリクサーの軽い順に並べる() {
        List<CardService.CardGroup> groups = cardService.catalog();

        assertEquals("common", groups.get(0).rarity());
        assertEquals(List.of(5, 3), ids(groups.get(0)));
        assertEquals("epic", groups.get(1).rarity());
        // 鏡はエリクサーが固定でないため、同じレアリティの最後に来る。
        assertEquals(List.of(4, 1, 2), ids(groups.get(1)));
    }

    @Test
    void タワーユニットはレアリティに関係なく最後の1グループにまとめる() {
        List<CardService.CardGroup> groups = cardService.catalog();

        CardService.CardGroup last = groups.get(groups.size() - 1);
        assertNull(last.rarity());
        assertEquals(List.of(9), ids(last));
        assertEquals(3, groups.size());
    }

    @Test
    void 絞り込みの選択肢はコストを持つカードから重複なく昇順で作る() {
        // 鏡(null)とタワーユニットは含めない。
        assertEquals(List.of(1, 2, 3, 5), cardService.elixirCosts());
    }

    @Test
    void 使用率順はレアリティをまたいで使った人数の多い順に並べタワーユニットは別グループにする() {
        // カード4は2人・1と3は1人・2と5は0人。同じ人数ならレアリティ別の一覧と同じ順(3が1より先)。
        CardUsage usage = CardUsage.of(new TopDecks(Instant.parse("2026-10-11T00:00:00Z"), List.of(
                new TopDecks.SampledDeck(List.of(4, 1), 9),
                new TopDecks.SampledDeck(List.of(4, 3), null))));

        List<CardService.CardGroup> groups = cardService.byUsage(usage);

        assertEquals(2, groups.size());
        assertEquals("", groups.get(0).rarity());
        assertEquals(List.of(4, 3, 1, 5, 2), ids(groups.get(0)));
        assertNull(groups.get(1).rarity());
        assertEquals(List.of(9), ids(groups.get(1)));
    }

    @Test
    void 詳細はタワーユニットも引ける() {
        assertEquals("Tower Princess", cardService.byId(9).name());
        assertThrows(CardNotFoundException.class, () -> cardService.byId(999));
    }

    private static List<Integer> ids(CardService.CardGroup group) {
        return group.cards().stream().map(CardsResponse.Card::id).toList();
    }

    private static CardsResponse.Card card(int id, String name, String rarity, Integer elixirCost) {
        return new CardsResponse.Card(id, name, 14, null, elixirCost, rarity, null);
    }
}
