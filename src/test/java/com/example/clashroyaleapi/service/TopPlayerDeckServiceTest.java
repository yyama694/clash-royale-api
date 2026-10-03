package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.domain.TopDecks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TopPlayerDeckServiceTest {

    private static final Instant COLLECTED_AT = Instant.parse("2026-09-27T03:43:58Z");
    private static final int GOLDEN_KNIGHT = 26000074;
    private static final int TOWER_PRINCESS = 159000000;

    private CardUsageService usageService;
    private TopPlayerDeckService service;

    @BeforeEach
    void setUp() {
        usageService = mock(CardUsageService.class);
        service = new TopPlayerDeckService(usageService);
    }

    private static TopDecks.SampledDeck deck(int rank, List<Integer> cards, Integer tower) {
        return new TopDecks.SampledDeck(cards, tower,
                new TopDecks.Player("#P" + rank, "p" + rank, rank, 3000 - rank, List.of(), null));
    }

    private void given(List<TopDecks.SampledDeck> decks) {
        when(usageService.topDecks()).thenReturn(Optional.of(new TopDecks(COLLECTED_AT, decks)));
    }

    private static List<Integer> ranks(TopPlayerDeckService.Page page) {
        return page.decks().stream().map(deck -> deck.player().rank()).toList();
    }

    @Test
    void 順位の順に並べる() {
        given(List.of(deck(3, List.of(1), null), deck(1, List.of(1), null), deck(2, List.of(1), null)));

        TopPlayerDeckService.Page page = service.page(null, 1).orElseThrow();

        assertEquals(List.of(1, 2, 3), ranks(page));
        assertEquals(COLLECTED_AT, page.collectedAt());
        assertEquals(3, page.total());
        assertEquals(1, page.from());
        assertFalse(page.hasNext());
    }

    @Test
    void カードで絞り込むとタワーユニットも対象にする() {
        given(List.of(
                deck(1, List.of(GOLDEN_KNIGHT, 2), null),
                deck(2, List.of(3, 4), TOWER_PRINCESS),
                deck(3, List.of(5, 6), null)));

        assertEquals(List.of(1), ranks(service.page(GOLDEN_KNIGHT, 1).orElseThrow()));
        assertEquals(List.of(2), ranks(service.page(TOWER_PRINCESS, 1).orElseThrow()));
    }

    @Test
    void 該当者がいなければ0人のページを返す() {
        given(List.of(deck(1, List.of(1), null)));

        TopPlayerDeckService.Page page = service.page(999, 1).orElseThrow();

        assertTrue(page.decks().isEmpty());
        assertEquals(0, page.total());
        assertEquals(0, page.from());
        assertEquals(1, page.sampleSize());
        assertNull(page.topRank());
    }

    @Test
    void 絞り込む前の人数と絞り込み後の最上位の順位を返す() {
        List<TopDecks.SampledDeck> decks = new ArrayList<>();
        IntStream.rangeClosed(1, 150).forEach(rank -> decks.add(deck(rank, List.of(rank < 5 ? 1 : 2), null)));
        given(decks);

        TopPlayerDeckService.Page second = service.page(2, 2).orElseThrow();

        assertEquals(150, second.sampleSize());
        assertEquals(146, second.total());
        // 2ページ目を開いても、そのページの先頭ではなく絞り込んだ全員の中の最上位
        assertEquals(5, second.topRank());
    }

    @Test
    void 百人ずつ区切り範囲外のページは最後のページにする() {
        List<TopDecks.SampledDeck> decks = new ArrayList<>();
        IntStream.rangeClosed(1, 250).forEach(rank -> decks.add(deck(rank, List.of(1), null)));
        given(decks);

        TopPlayerDeckService.Page second = service.page(null, 2).orElseThrow();
        assertEquals(101, second.from());
        assertEquals(100, second.decks().size());
        assertTrue(second.hasNext());

        TopPlayerDeckService.Page beyond = service.page(null, 99).orElseThrow();
        assertEquals(3, beyond.page());
        assertEquals(201, beyond.from());
        assertEquals(50, beyond.decks().size());
        assertFalse(beyond.hasNext());

        assertEquals(1, service.page(null, 0).orElseThrow().page());
    }

    @Test
    void 集計が無いか古い形式しか無ければ空() {
        when(usageService.topDecks()).thenReturn(Optional.empty());
        assertTrue(service.page(null, 1).isEmpty());

        given(List.of(new TopDecks.SampledDeck(List.of(1), null)));
        assertTrue(service.page(null, 1).isEmpty());
    }
}
