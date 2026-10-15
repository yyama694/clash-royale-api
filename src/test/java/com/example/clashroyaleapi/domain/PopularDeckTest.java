package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PopularDeckTest {

    private static final List<Integer> A = List.of(1, 2, 3, 4, 5, 6, 7, 8);
    private static final List<Integer> B = List.of(11, 12, 13, 14, 15, 16, 17, 18);
    private static final List<Integer> C = List.of(21, 22, 23, 24, 25, 26, 27, 28);

    private static TopDecks.SampledDeck deck(int rank, List<Integer> cards) {
        return new TopDecks.SampledDeck(cards, null,
                new TopDecks.Player("#P" + rank, "p" + rank, rank, 3000 - rank, List.of(), null));
    }

    @Test
    void 並び順が違っても同じ8枚なら束ね_最上位の人のデッキを代表にする() {
        List<PopularDeck> popular = PopularDeck.rank(List.of(
                deck(1, A), deck(2, A.reversed()), deck(3, B)), 2);

        assertEquals(1, popular.size());
        assertEquals(2, popular.getFirst().players());
        assertEquals(1, popular.getFirst().best().player().rank());
    }

    @Test
    void 人数の多い順で同じ人数なら最上位の人の順位が高い方を先にする() {
        List<PopularDeck> popular = PopularDeck.rank(List.of(
                deck(1, B), deck(2, A), deck(3, C), deck(4, A), deck(5, C), deck(6, B), deck(7, A)), 2);

        assertEquals(List.of(2, 1, 3), popular.stream().map(p -> p.best().player().rank()).toList());
        assertEquals(List.of(3, 2, 2), popular.stream().map(PopularDeck::players).toList());
    }

    @Test
    void 下限の人数に満たないものと8枚そろっていないものは出さない() {
        List<PopularDeck> popular = PopularDeck.rank(List.of(
                deck(1, A), deck(2, A), deck(3, B), deck(4, B), deck(5, C),
                deck(6, List.of(1, 2, 3)), deck(7, List.of(1, 2, 3))), 2);

        assertEquals(List.of(1, 3), popular.stream().map(p -> p.best().player().rank()).toList());
    }
}
