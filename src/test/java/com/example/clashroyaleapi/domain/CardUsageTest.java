package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardUsageTest {

    private static final Instant AT = Instant.parse("2026-09-26T00:00:00Z");

    private static TopDecks.SampledDeck deck(Integer tower, Integer... cards) {
        return new TopDecks.SampledDeck(List.of(cards), tower);
    }

    @Test
    void 使用率はデッキに入れていた人数を標本数で割った値() {
        CardUsage usage = CardUsage.of(new TopDecks(AT, List.of(
                deck(null, 1, 2), deck(null, 1, 3), deck(null, 2, 3), deck(null, 1, 4))));

        CardUsage.Usage card = usage.usageOf(1);

        assertEquals(3, card.users());
        assertEquals(4, card.sampleSize());
        assertEquals(75.0, card.percent());
    }

    @Test
    void 順位は使用者の多い順で同数は同順位() {
        CardUsage usage = CardUsage.of(new TopDecks(AT, List.of(
                deck(null, 1, 2), deck(null, 1, 3), deck(null, 2, 3), deck(null, 1, 4))));

        assertEquals(1, usage.usageOf(1).rank());
        assertEquals(2, usage.usageOf(2).rank());
        assertEquals(2, usage.usageOf(3).rank());
        assertEquals(4, usage.usageOf(4).rank());
        assertEquals(4, usage.usageOf(1).rankedOf());
    }

    @Test
    void 誰も使っていないカードは0人で順位なし() {
        CardUsage usage = CardUsage.of(new TopDecks(AT, List.of(deck(null, 1, 2))));

        CardUsage.Usage card = usage.usageOf(99);

        assertEquals(0, card.users());
        assertEquals(0, card.rank());
        assertEquals(1, card.sampleSize());
    }

    @Test
    void タワーユニットは通常のカードとは別に順位を付ける() {
        CardUsage usage = CardUsage.of(new TopDecks(AT, List.of(
                deck(100, 1, 2), deck(100, 1, 3), deck(200, 1, 3), deck(null, 1, 4))));

        assertEquals(1, usage.usageOf(100).rank());
        assertEquals(2, usage.usageOf(100).rankedOf());
        assertEquals(50.0, usage.usageOf(100).percent());
        assertEquals(1, usage.usageOf(1).rank());
    }

    @Test
    void 一緒に使われるカードは使用者の割合が高い順に5枚まで() {
        List<TopDecks.SampledDeck> decks = new ArrayList<>();
        // カード1を10人が使い、全員がカード2、7人がカード3、半分がカード4〜8を使う。
        IntStream.range(0, 10).forEach(i -> decks.add(deck(null,
                i < 7 ? new Integer[] {1, 2, 3, 4 + i % 5} : new Integer[] {1, 2, 4 + i % 5})));

        List<CardUsage.Partner> partners = CardUsage.of(new TopDecks(AT, decks)).usageOf(1).partners();

        assertEquals(CardUsage.PARTNER_LIMIT, partners.size());
        assertEquals(2, partners.get(0).cardId());
        assertEquals(100, partners.get(0).percent());
        assertEquals(3, partners.get(1).cardId());
        assertEquals(70, partners.get(1).percent());
    }

    @Test
    void 使用者が少ないカードは一緒に使われるカードを出さない() {
        List<TopDecks.SampledDeck> decks = new ArrayList<>();
        IntStream.range(0, CardUsage.MIN_USERS_FOR_PARTNERS - 1).forEach(i -> decks.add(deck(null, 1, 2)));

        assertTrue(CardUsage.of(new TopDecks(AT, decks)).usageOf(1).partners().isEmpty());
    }

    @Test
    void 同じデッキに同じカードが重複していても1人と数える() {
        CardUsage usage = CardUsage.of(new TopDecks(AT, List.of(deck(null, 1, 1, 2))));

        assertEquals(1, usage.usageOf(1).users());
        assertEquals(100.0, usage.usageOf(1).percent());
    }
}
