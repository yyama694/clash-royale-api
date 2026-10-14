package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimilarDecksTest {

    private static final List<Integer> DECK = List.of(1, 2, 3, 4, 5, 6, 7, 8);

    private static TopDecks.SampledDeck deck(String tag, Integer... cards) {
        return new TopDecks.SampledDeck(List.of(cards), null,
                new TopDecks.Player(tag, tag, 1, 3000, List.of(), null));
    }

    @Test
    void 八枚とも同じ人を本人を除いて数える() {
        SimilarDecks similar = SimilarDecks.find(DECK, List.of(
                deck("#A", 8, 7, 6, 5, 4, 3, 2, 1), deck("#B", 1, 2, 3, 4, 5, 6, 7, 8),
                deck("#ME", 1, 2, 3, 4, 5, 6, 7, 8), deck("#C", 1, 2, 3, 4, 5, 6, 7, 9)), "me").orElseThrow();

        assertEquals(8, similar.sharedCards());
        assertEquals(2, similar.players());
        assertEquals(4, similar.sampleSize());
        assertTrue(similar.selfExcluded());
    }

    @Test
    void 八枚が同じ人がいなければ枚数を減らし_一番多くの人が使う組み合わせを元の並び順で返す() {
        SimilarDecks similar = SimilarDecks.find(DECK, List.of(
                deck("#A", 1, 2, 3, 4, 5, 6, 7, 9), deck("#B", 9, 2, 3, 4, 5, 6, 7, 8),
                deck("#C", 10, 2, 3, 4, 5, 6, 7, 8)), "#ME").orElseThrow();

        assertEquals(List.of(2, 3, 4, 5, 6, 7, 8), similar.cardIds());
        assertEquals(2, similar.players());
        assertFalse(similar.selfExcluded());
    }

    @Test
    void 六枚より少なくしか同じでなければ空() {
        assertEquals(Optional.empty(), SimilarDecks.find(DECK, List.of(
                deck("#A", 1, 2, 3, 4, 5, 9, 10, 11)), "#ME"));
    }

    @Test
    void 本人しかいなければ空() {
        assertEquals(Optional.empty(), SimilarDecks.find(DECK, List.of(
                deck("#ME", 1, 2, 3, 4, 5, 6, 7, 8)), "#ME"));
    }

    @Test
    void 使用中のデッキが8枚そろっていなければ空() {
        assertEquals(Optional.empty(), SimilarDecks.find(List.of(1, 2, 3, 4, 5, 6, 7), List.of(
                deck("#A", 1, 2, 3, 4, 5, 6, 7, 8)), "#ME"));
    }

    @Test
    void 組み合わせは並び順を保って全部作る() {
        assertEquals(List.of(List.of(1, 2), List.of(1, 3), List.of(2, 3)), SimilarDecks.subsets(List.of(1, 2, 3), 2));
        assertEquals(28, SimilarDecks.subsets(DECK, 6).size());
    }
}
