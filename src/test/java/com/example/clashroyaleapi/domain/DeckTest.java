package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeckTest {

    private static final List<Integer> COSTS = List.of(4, 2, 2, 3, 5, 4, 3, 6);

    @Test
    void 平均エリクサーは8枚の平均() {
        assertEquals(3.625, Deck.averageElixir(COSTS).getAsDouble());
    }

    @Test
    void 平均レベルは8枚の平均() {
        assertEquals(13.5, Deck.averageLevel(List.of(14, 14, 14, 14, 13, 13, 13, 13)).getAsDouble());
    }

    @Test
    void 四枚サイクルは軽い順に4枚の合計() {
        assertEquals(10, Deck.fourCardCycle(COSTS).getAsInt());
    }

    /** 一部のカードだけで計算すると、ゲーム内の数字と食い違うため出さない。 */
    @Test
    void エリクサーが分からないカードを含むと指標を出さない() {
        List<Integer> withUnknown = Arrays.asList(4, 2, 2, 3, 5, 4, 3, null);
        assertEquals(OptionalDouble.empty(), Deck.averageElixir(withUnknown));
        assertEquals(OptionalInt.empty(), Deck.fourCardCycle(withUnknown));
    }

    @Test
    void 八枚でないデッキは指標もリンクも出さない() {
        assertEquals(OptionalDouble.empty(), Deck.averageElixir(List.of(3, 3, 3)));
        assertEquals(OptionalInt.empty(), Deck.fourCardCycle(List.of(3, 3, 3)));
        assertEquals(OptionalDouble.empty(), Deck.averageLevel(List.of(14, 14, 14)));
        assertEquals(Optional.empty(), Deck.copyUrl(List.of(26000000, 26000001), null));
    }

    /** RoyaleAPIのコピーボタンのリンク(2026-09-19に確認)と同じ形になること。 */
    @Test
    void コピー用リンクはカードの並び順とタワーユニットを保つ() {
        List<Integer> ids = List.of(26000036, 26000074, 26000050, 26000106, 26000042, 26000046, 27000010, 28000001);
        assertEquals(Optional.of("https://link.clashroyale.com/en?clashroyale://copyDeck?deck="
                        + "26000036;26000074;26000050;26000106;26000042;26000046;27000010;28000001"
                        + "&tt=159000000&l=Royals"),
                Deck.copyUrl(ids, 159000000));
    }

    @Test
    void タワーユニットが無ければttを付けない() {
        List<Integer> ids = List.of(1, 2, 3, 4, 5, 6, 7, 8);
        assertEquals(Optional.of("https://link.clashroyale.com/en?clashroyale://copyDeck?deck=1;2;3;4;5;6;7;8&l=Royals"),
                Deck.copyUrl(ids, null));
    }
}
