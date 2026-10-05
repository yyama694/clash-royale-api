package com.example.clashroyaleapi.crawler;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;
import com.example.clashroyaleapi.client.exception.ApiRateLimitException;
import com.example.clashroyaleapi.client.exception.ResourceNotFoundException;
import com.example.clashroyaleapi.config.CardUsageProperties;
import com.example.clashroyaleapi.domain.CardForm;
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
import java.util.ArrayList;
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
        when(usageService.topDecks()).thenReturn(Optional.empty());
        clock = new MutableClock(Instant.parse("2026-09-26T00:00:00Z"));
        collector = new TopDeckCollector(apiClient, usageService,
                new CardUsageProperties(true, Duration.ofSeconds(3), Duration.ofHours(24), 1000), clock);
    }

    private static PlayerRankingResponse.RankedPlayer ranked(String tag) {
        return new PlayerRankingResponse.RankedPlayer(tag, "name", 1, 13, null);
    }

    private static TopDecks.Player playerInfo() {
        return new TopDecks.Player("#A", "name", 1, 3000, List.of(16), null);
    }

    private static BattleLogEntry.Card card(int id) {
        return new BattleLogEntry.Card(id, "card", 14, 14, 3, null, null);
    }

    /** 先頭を進化(限界突破)、2枚目をヒーローで使ったデッキ。 */
    private static List<BattleLogEntry.Card> deckWithForms(int firstId) {
        List<BattleLogEntry.Card> cards = new ArrayList<>(deck(firstId, 8));
        cards.set(0, cards.get(0).withEvolutionLevel(1));
        cards.set(1, cards.get(1).withEvolutionLevel(2));
        return cards;
    }

    private static List<BattleLogEntry.Card> deck(int firstId, int size) {
        return IntStream.range(firstId, firstId + size).mapToObj(TopDeckCollectorTest::card).toList();
    }

    private static BattleLogEntry battle(String type, List<BattleLogEntry.Card> cards,
            List<BattleLogEntry.Card> support) {
        return new BattleLogEntry(type, "20260926T000000.000Z", null,
                List.of(new BattleLogEntry.Participant("#P", "name", 1, cards, support, null)), List.of(), null);
    }

    private static BattleLogEntry ranked(List<BattleLogEntry.Card> cards, List<BattleLogEntry.Card> support) {
        return battle("pathOfLegend", cards, support);
    }

    @Test
    void 上位全員の直近のランク戦のデッキを1人ずつ集めてから渡す() {
        when(apiClient.getPathOfLegendRankings("global", 1000)).thenReturn(List.of(
                new PlayerRankingResponse.RankedPlayer("#A", "Miku", 2887, 1, null), ranked("#B")));
        // 対戦履歴は新しい順。ランク戦より新しいフレンドバトルのデッキは使わない。
        when(apiClient.getBattleLogUncached("#A")).thenReturn(List.of(
                battle("friendly", deck(100, 8), List.of()),
                ranked(deckWithForms(0), List.of(card(159000000))),
                ranked(deck(200, 8), List.of())));
        when(apiClient.getBattleLogUncached("#B")).thenReturn(List.of(ranked(deck(0, 8), List.of())));

        collector.collectNext();
        collector.collectNext();
        verify(usageService, never()).publish(any());
        collector.collectNext();

        ArgumentCaptor<TopDecks> captor = ArgumentCaptor.forClass(TopDecks.class);
        verify(usageService).publish(captor.capture());
        List<TopDecks.SampledDeck> decks = captor.getValue().decks();
        assertEquals(2, decks.size());
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), decks.get(0).cardIds());
        assertEquals(159000000, decks.get(0).towerTroopId());
        assertNull(decks.get(1).towerTroopId());
        // レベルは公式APIの値(14/14)をゲーム内表記に直して残す。形はその対戦で使った形。
        assertEquals(new TopDecks.Player("#A", "Miku", 1, 2887, List.of(16, 16, 16, 16, 16, 16, 16, 16), 16,
                        List.of(CardForm.EVOLUTION, CardForm.HERO, CardForm.NORMAL, CardForm.NORMAL,
                                CardForm.NORMAL, CardForm.NORMAL, CardForm.NORMAL, CardForm.NORMAL)),
                decks.get(0).player());
        assertNull(decks.get(1).player().towerLevel());
    }

    @Test
    void 八枚そろっていないランク戦は飛ばして次のランク戦を使う() {
        when(apiClient.getPathOfLegendRankings("global", 1000)).thenReturn(List.of(ranked("#A")));
        when(apiClient.getBattleLogUncached("#A")).thenReturn(List.of(
                ranked(deck(100, 7), List.of()),
                ranked(deck(0, 8), List.of())));

        collector.collectNext();
        collector.collectNext();

        ArgumentCaptor<TopDecks> captor = ArgumentCaptor.forClass(TopDecks.class);
        verify(usageService).publish(captor.capture());
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), captor.getValue().decks().get(0).cardIds());
    }

    @Test
    void ランク戦をしていない人と見つからないプレイヤーは標本に入れない() {
        when(apiClient.getPathOfLegendRankings("global", 1000))
                .thenReturn(List.of(ranked("#A"), ranked("#B"), ranked("#C")));
        when(apiClient.getBattleLogUncached("#A")).thenReturn(List.of(ranked(deck(0, 8), null)));
        when(apiClient.getBattleLogUncached("#B")).thenReturn(List.of(battle("clanMate", deck(0, 8), null)));
        when(apiClient.getBattleLogUncached("#C")).thenThrow(new ResourceNotFoundException("gone", null));

        IntStream.range(0, 4).forEach(i -> collector.collectNext());

        ArgumentCaptor<TopDecks> captor = ArgumentCaptor.forClass(TopDecks.class);
        verify(usageService).publish(captor.capture());
        assertEquals(1, captor.getValue().decks().size());
    }

    @Test
    void 前回の集計から時間が経っていなければ何もしない() {
        TopDecks recent = new TopDecks(clock.instant().minus(Duration.ofHours(1)),
                List.of(new TopDecks.SampledDeck(List.of(1), null, playerInfo())));
        when(usageService.topDecks()).thenReturn(Optional.of(recent));

        collector.collectNext();

        verify(apiClient, never()).getPathOfLegendRankings(anyString(), anyInt());
    }

    @Test
    void 前回の集計から時間が経っていれば集め直す() {
        TopDecks old = new TopDecks(clock.instant().minus(Duration.ofHours(25)),
                List.of(new TopDecks.SampledDeck(List.of(1), null, playerInfo())));
        when(usageService.topDecks()).thenReturn(Optional.of(old));

        collector.collectNext();

        verify(apiClient).getPathOfLegendRankings("global", 1000);
    }

    @Test
    void 誰のデッキかを持たない古い形式の集計なら時間が経っていなくても集め直す() {
        TopDecks legacy = new TopDecks(clock.instant().minus(Duration.ofHours(1)),
                List.of(new TopDecks.SampledDeck(List.of(1), null)));
        when(usageService.topDecks()).thenReturn(Optional.of(legacy));

        collector.collectNext();

        verify(apiClient).getPathOfLegendRankings("global", 1000);
    }

    @Test
    void 呼び出し制限に当たったら少し待ってから同じ人をやり直す() {
        when(apiClient.getPathOfLegendRankings("global", 1000)).thenReturn(List.of(ranked("#A")));
        when(apiClient.getBattleLogUncached("#A"))
                .thenThrow(new ApiRateLimitException("too many requests", null))
                .thenReturn(List.of(ranked(deck(0, 8), null)));

        collector.collectNext();
        collector.collectNext();
        collector.collectNext();
        verify(apiClient, times(1)).getBattleLogUncached("#A");

        clock.advance(Duration.ofMinutes(2));
        collector.collectNext();

        verify(apiClient, times(2)).getBattleLogUncached("#A");
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
