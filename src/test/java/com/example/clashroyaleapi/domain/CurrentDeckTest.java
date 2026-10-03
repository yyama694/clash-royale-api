package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.PlayerResponse;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentDeckTest {

    private static final int CHAMPION = 26000074;

    private static BattleLogEntry.Card card(int id, int level) {
        return new BattleLogEntry.Card(id, "card", level, 14, 3, null, null);
    }

    private static List<BattleLogEntry.Card> cards(int... ids) {
        return IntStream.of(ids).mapToObj(id -> card(id, 14)).toList();
    }

    private static PlayerResponse player(List<BattleLogEntry.Card> deck) {
        return new PlayerResponse("#ABC", "name", 15, 9000, 9000, 0, 0, 0, null, deck, cards(159000000), null, null,
                List.of());
    }

    private static BattleLogEntry battle(String tag, List<BattleLogEntry.Card> deck) {
        return new BattleLogEntry("pathOfLegend", "20260927T000000.000Z", null,
                List.of(new BattleLogEntry.Participant(tag, "name", 1, deck, List.of())), List.of(), null);
    }

    private static List<Integer> ids(CurrentDeck deck) {
        return deck.cards().stream().map(BattleLogEntry.Card::id).toList();
    }

    @Test
    void 八枚そろっていればそのまま使う() {
        CurrentDeck deck = CurrentDeck.of(player(cards(1, 2, 3, 4, 5, 6, 7, 8)), List.of()).orElseThrow();

        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), ids(deck));
        assertFalse(deck.completedFromBattle());
        assertEquals(1, deck.supportCards().size());
    }

    @Test
    void 抜けたカードを直近の対戦の同じ位置に補う() {
        PlayerResponse player = player(cards(1, 3, 4, 5, 6, 7, 8));
        List<BattleLogEntry> log = List.of(battle("#ABC", cards(1, CHAMPION, 3, 4, 5, 6, 7, 8)));

        CurrentDeck deck = CurrentDeck.of(player, log).orElseThrow();

        assertEquals(List.of(1, CHAMPION, 3, 4, 5, 6, 7, 8), ids(deck));
        assertTrue(deck.completedFromBattle());
    }

    @Test
    void 今のデッキのカードはレベルを今の値のまま残す() {
        List<BattleLogEntry.Card> current = List.of(card(1, 16), card(3, 16), card(4, 16), card(5, 16), card(6, 16),
                card(7, 16), card(8, 16));
        List<BattleLogEntry> log = List.of(battle("#ABC", cards(1, CHAMPION, 3, 4, 5, 6, 7, 8)));

        CurrentDeck deck = CurrentDeck.of(player(current), log).orElseThrow();

        assertEquals(16, deck.cards().get(0).level());
        assertEquals(14, deck.cards().get(1).level());
    }

    @Test
    void 今のデッキのカードをすべて含む対戦だけを使う() {
        PlayerResponse player = player(cards(1, 3, 4, 5, 6, 7, 8));
        List<BattleLogEntry> log = List.of(
                battle("#ABC", cards(1, 2, 3, 4, 5, 6, 7, 9)),
                battle("#ABC", cards(1, CHAMPION, 3, 4, 5, 6, 7, 8)));

        assertEquals(List.of(1, CHAMPION, 3, 4, 5, 6, 7, 8), ids(CurrentDeck.of(player, log).orElseThrow()));
    }

    @Test
    void 二対二と他人のデッキからは補わない() {
        PlayerResponse player = player(cards(1, 3, 4, 5, 6, 7, 8));
        BattleLogEntry twoVsTwo = new BattleLogEntry("clanMate2v2", "20260927T000000.000Z", null, List.of(
                new BattleLogEntry.Participant("#ABC", "name", 1, cards(1, CHAMPION, 3, 4, 5, 6, 7, 8), List.of()),
                new BattleLogEntry.Participant("#MATE", "mate", 1, cards(11, 12, 13, 14, 15, 16, 17, 18), List.of())),
                List.of(), null);
        List<BattleLogEntry> log = List.of(twoVsTwo, battle("#OTHER", cards(1, CHAMPION, 3, 4, 5, 6, 7, 8)));

        CurrentDeck deck = CurrentDeck.of(player, log).orElseThrow();

        assertEquals(7, deck.cards().size());
        assertFalse(deck.completedFromBattle());
    }

    @Test
    void タグの表記違いは同じプレイヤーとして扱う() {
        PlayerResponse player = player(cards(1, 3, 4, 5, 6, 7, 8));
        List<BattleLogEntry> log = List.of(battle("#abc", cards(1, CHAMPION, 3, 4, 5, 6, 7, 8)));

        assertTrue(CurrentDeck.of(player, log).orElseThrow().completedFromBattle());
    }

    private static List<BattleLogEntry.Card> withLevels(List<BattleLogEntry.Card> cards, Integer... evolutionLevels) {
        return IntStream.range(0, cards.size())
                .mapToObj(i -> cards.get(i).withEvolutionLevel(i < evolutionLevels.length ? evolutionLevels[i] : null))
                .toList();
    }

    private static List<Integer> evolutionLevels(CurrentDeck deck) {
        return deck.cards().stream().map(BattleLogEntry.Card::evolutionLevel).toList();
    }

    @Test
    void 形は同じ並びで戦った直近の対戦から取る() {
        // 公式APIの currentDeck は持っている形を返す(8枚目にも進化があり、2枚目は進化とヒーローの両方を持つ3)。
        PlayerResponse player = player(withLevels(cards(1, 2, 3, 4, 5, 6, 7, 8), 1, 3, 1, null, null, null, null, 1));
        List<BattleLogEntry> log = List.of(battle("#ABC", withLevels(cards(1, 2, 3, 4, 5, 6, 7, 8), 1, 2, 1)));

        CurrentDeck deck = CurrentDeck.of(player, log).orElseThrow();

        assertEquals(Arrays.asList(1, 2, 1, null, null, null, null, null), evolutionLevels(deck));
    }

    @Test
    void 同じ並びの対戦が無ければ通常の形として扱う() {
        PlayerResponse player = player(withLevels(cards(1, 2, 3, 4, 5, 6, 7, 8), 1, 2, 1));
        // 同じ8枚でも並びが違えば、枠が違うので使わない。
        List<BattleLogEntry> log = List.of(battle("#ABC", withLevels(cards(2, 1, 3, 4, 5, 6, 7, 8), 1, 2, 1)));

        CurrentDeck deck = CurrentDeck.of(player, log).orElseThrow();

        assertEquals(Collections.nCopies(8, null), evolutionLevels(deck));
    }

    @Test
    void 補ったデッキの形も補った対戦から取る() {
        PlayerResponse player = player(withLevels(cards(1, 3, 4, 5, 6, 7, 8), 1, 1));
        List<BattleLogEntry> log = List.of(battle("#ABC", withLevels(cards(1, CHAMPION, 3, 4, 5, 6, 7, 8), 1, null, 1)));

        CurrentDeck deck = CurrentDeck.of(player, log).orElseThrow();

        assertEquals(Arrays.asList(1, null, 1, null, null, null, null, null), evolutionLevels(deck));
    }

    @Test
    void デッキが返らなければ空() {
        assertTrue(CurrentDeck.of(player(List.of()), List.of()).isEmpty());
        assertTrue(CurrentDeck.of(player(null), List.of()).isEmpty());
    }
}
