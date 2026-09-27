package com.example.clashroyaleapi.crawler;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.client.exception.ApiRateLimitException;
import com.example.clashroyaleapi.client.exception.ResourceNotFoundException;
import com.example.clashroyaleapi.config.CardUsageProperties;
import com.example.clashroyaleapi.domain.CardUsage;
import com.example.clashroyaleapi.domain.TopDecks;
import com.example.clashroyaleapi.service.CardUsageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TopDeckCollectorTest {

    private ClashRoyaleApiClient apiClient;
    private CardUsageService usageService;
    private MutableClock clock;
    private TopDeckCollector collector;

    @BeforeEach
    void setUp() {
        apiClient = mock(ClashRoyaleApiClient.class);
        usageService = mock(CardUsageService.class);
        when(usageService.current()).thenReturn(Optional.empty());
        clock = new MutableClock(Instant.parse("2026-09-26T00:00:00Z"));
        collector = new TopDeckCollector(apiClient, usageService,
                new CardUsageProperties(true, Duration.ofSeconds(3), Duration.ofHours(24), 1000), clock);
    }

    private static PlayerRankingResponse.RankedPlayer ranked(String tag) {
        return new PlayerRankingResponse.RankedPlayer(tag, "name", 1, 1, 13, null);
    }

    private static BattleLogEntry.Card card(int id) {
        return new BattleLogEntry.Card(id, "card", 14, 14, 3, null);
    }

    private static PlayerResponse player(List<BattleLogEntry.Card> deck, List<BattleLogEntry.Card> support) {
        return new PlayerResponse("#P", "name", 50, 9000, 9000, 0, 0, 0, null, deck, support, null, null, null,
                List.of());
    }

    private static List<BattleLogEntry.Card> fullDeck() {
        return IntStream.range(0, 8).mapToObj(TopDeckCollectorTest::card).toList();
    }

    @Test
    void 上位全員のデッキを1人ずつ集めてから渡す() {
        when(apiClient.getPathOfLegendRankings("global", 1000)).thenReturn(List.of(ranked("#A"), ranked("#B")));
        when(apiClient.getPlayerUncached("#A")).thenReturn(player(fullDeck(), List.of(card(159000000))));
        when(apiClient.getPlayerUncached("#B")).thenReturn(player(fullDeck(), List.of()));

        collector.collectNext();
        collector.collectNext();
        verify(usageService, never()).publish(any());
        collector.collectNext();

        ArgumentCaptor<TopDecks> captor = ArgumentCaptor.forClass(TopDecks.class);
        verify(usageService).publish(captor.capture());
        List<TopDecks.SampledDeck> decks = captor.getValue().decks();
        assertEquals(2, decks.size());
        assertEquals(159000000, decks.get(0).towerTroopId());
        assertNull(decks.get(1).towerTroopId());
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), decks.get(0).cardIds());
    }

    @Test
    void 八枚そろっていないデッキと見つからないプレイヤーは標本に入れない() {
        when(apiClient.getPathOfLegendRankings("global", 1000))
                .thenReturn(List.of(ranked("#A"), ranked("#B"), ranked("#C")));
        when(apiClient.getPlayerUncached("#A")).thenReturn(player(fullDeck(), null));
        when(apiClient.getPlayerUncached("#B")).thenReturn(player(List.of(card(1)), null));
        when(apiClient.getPlayerUncached("#C")).thenThrow(new ResourceNotFoundException("gone", null));

        IntStream.range(0, 4).forEach(i -> collector.collectNext());

        ArgumentCaptor<TopDecks> captor = ArgumentCaptor.forClass(TopDecks.class);
        verify(usageService).publish(captor.capture());
        assertEquals(1, captor.getValue().decks().size());
    }

    @Test
    void 前回の集計から時間が経っていなければ何もしない() {
        CardUsage recent = CardUsage.of(new TopDecks(clock.instant().minus(Duration.ofHours(1)), List.of()));
        when(usageService.current()).thenReturn(Optional.of(recent));

        collector.collectNext();

        verify(apiClient, never()).getPathOfLegendRankings(anyString(), anyInt());
    }

    @Test
    void 前回の集計から時間が経っていれば集め直す() {
        CardUsage old = CardUsage.of(new TopDecks(clock.instant().minus(Duration.ofHours(25)), List.of()));
        when(usageService.current()).thenReturn(Optional.of(old));

        collector.collectNext();

        verify(apiClient).getPathOfLegendRankings("global", 1000);
    }

    @Test
    void 呼び出し制限に当たったら少し待ってから同じ人をやり直す() {
        when(apiClient.getPathOfLegendRankings("global", 1000)).thenReturn(List.of(ranked("#A")));
        when(apiClient.getPlayerUncached("#A"))
                .thenThrow(new ApiRateLimitException("too many requests", null))
                .thenReturn(player(fullDeck(), null));

        collector.collectNext();
        collector.collectNext();
        collector.collectNext();
        verify(apiClient, times(1)).getPlayerUncached("#A");

        clock.advance(Duration.ofMinutes(2));
        collector.collectNext();

        verify(apiClient, times(2)).getPlayerUncached("#A");
        verify(usageService).publish(any());
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
