package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeckWinRateTallyTest {

    private static final Instant SEASON_START = Instant.parse("2026-10-05T09:00:00Z");
    private static final List<DeckWinRates.TopPlayer> TOP = List.of(
            new DeckWinRates.TopPlayer("#A", "Miku", 1),
            new DeckWinRates.TopPlayer("#B", "Rin", 2),
            new DeckWinRates.TopPlayer("#C", "Len", 3));

    private static List<BattleLogEntry.Card> deck(int firstId) {
        return IntStream.range(firstId, firstId + 8)
                .mapToObj(id -> new BattleLogEntry.Card(id, "card", 14, 14, 3, null, null))
                .toList();
    }

    private static BattleLogEntry.Participant side(String tag, int crowns, List<BattleLogEntry.Card> cards) {
        return new BattleLogEntry.Participant(tag, "name", crowns, cards, List.of(), null);
    }

    private static BattleLogEntry battle(String time, BattleLogEntry.Participant team,
            BattleLogEntry.Participant opponent) {
        return new BattleLogEntry("pathOfLegend", time, null, List.of(team), List.of(opponent), null);
    }

    private static Map<Set<Integer>, DeckWinRates.DeckRecord> byDeck(DeckWinRates result) {
        return result.decks().stream()
                .collect(Collectors.toMap(deck -> Set.copyOf(deck.cardIds()), Function.identity()));
    }

    @Test
    void 上位どうしの対戦を両者の側から数え両者の対戦履歴に出た同じ対戦は一度だけ数える() {
        DeckWinRateTally tally = new DeckWinRateTally(TOP, SEASON_START, null);
        BattleLogEntry.Participant a = side("#A", 3, deck(0));
        BattleLogEntry.Participant b = side("#B", 1, deck(10));
        tally.add(List.of(battle("20261006T000000.000Z", a, b)));
        tally.add(List.of(battle("20261006T000000.000Z", b, a)));

        DeckWinRates result = tally.result(Instant.parse("2026-10-07T00:00:00Z"), null);

        assertEquals(1, result.battles());
        Map<Set<Integer>, DeckWinRates.DeckRecord> decks = byDeck(result);
        assertEquals(1, decks.get(Set.copyOf(List.of(0, 1, 2, 3, 4, 5, 6, 7))).wins());
        assertEquals(1, decks.get(Set.copyOf(List.of(0, 1, 2, 3, 4, 5, 6, 7))).battles());
        assertEquals(0, decks.get(Set.copyOf(List.of(10, 11, 12, 13, 14, 15, 16, 17))).wins());
    }

    @Test
    void 上位でない相手との対戦と集めたシーズンの外の対戦と同じデッキどうしの対戦は数えない() {
        DeckWinRateTally tally = new DeckWinRateTally(TOP, SEASON_START, Instant.parse("2026-11-02T09:00:00Z"));
        tally.add(List.of(
                battle("20261006T000000.000Z", side("#A", 1, deck(0)), side("#Z", 0, deck(10))),
                battle("20261005T085959.000Z", side("#A", 1, deck(0)), side("#B", 0, deck(10))),
                battle("20261102T090000.000Z", side("#A", 1, deck(0)), side("#B", 0, deck(10))),
                battle("20261006T010000.000Z", side("#A", 1, deck(0)), side("#B", 0, deck(0))),
                battle("20261006T020000.000Z", side("#A", 1, deck(0)), side("#B", 0, deck(10)))));

        DeckWinRates result = tally.result(Instant.parse("2026-10-07T00:00:00Z"), null);

        assertEquals(1, result.battles());
        assertEquals(2, result.decks().size());
    }

    @Test
    void 引き分けは勝ちに数えない() {
        DeckWinRateTally tally = new DeckWinRateTally(TOP, SEASON_START, null);
        tally.add(List.of(battle("20261006T000000.000Z", side("#A", 1, deck(0)), side("#B", 1, deck(10)))));

        DeckWinRates result = tally.result(Instant.parse("2026-10-07T00:00:00Z"), null);

        assertEquals(0, result.decks().get(0).wins());
        assertEquals(0, result.decks().get(1).wins());
    }

    @Test
    void 表示には使った人のうち最上位の人の最も新しい対戦の並び順と形を使い人数と1人の最多の対戦数を数える() {
        DeckWinRateTally tally = new DeckWinRateTally(TOP, SEASON_START, null);
        List<BattleLogEntry.Card> reordered = new ArrayList<>(deck(0).reversed());
        reordered.set(0, reordered.get(0).withEvolutionLevel(1));
        tally.add(List.of(
                battle("20261006T030000.000Z", side("#C", 1, deck(0)), side("#B", 0, deck(10))),
                battle("20261006T020000.000Z", side("#B", 1, deck(0)), side("#C", 0, deck(20))),
                battle("20261006T010000.000Z", side("#B", 1, deck(0)), side("#A", 0, deck(30))),
                battle("20261006T040000.000Z", side("#B", 1, reordered), side("#A", 0, deck(40)))));

        DeckWinRates.DeckRecord shown = byDeck(tally.result(Instant.parse("2026-10-07T00:00:00Z"), null))
                .get(Set.copyOf(List.of(0, 1, 2, 3, 4, 5, 6, 7)));

        assertEquals(List.of(7, 6, 5, 4, 3, 2, 1, 0), shown.cardIds());
        assertEquals(CardForm.EVOLUTION, shown.formAt(0));
        assertEquals(new DeckWinRates.TopPlayer("#B", "Rin", 2), shown.top());
        assertEquals(4, shown.battles());
        assertEquals(2, shown.players());
        assertEquals(3, shown.maxPlayerBattles());
        assertNull(shown.towerTroopId());
    }

    private static DeckWinRates.DeckRecord record(int firstId, int battles, int wins, int players,
            int maxPlayerBattles) {
        return new DeckWinRates.DeckRecord(IntStream.range(firstId, firstId + 8).boxed().toList(), List.of(), null,
                new DeckWinRates.TopPlayer("#A", "Miku", 1), battles, wins, players, maxPlayerBattles);
    }

    @Test
    void 対戦数と人数が足りて1人に偏っていないデッキだけを勝率の高い順に出す() {
        DeckWinRates.DeckRecord listedLow = record(0, 100, 55, 10, 30);
        DeckWinRates.DeckRecord listedHigh = record(10, 50, 30, 10, 15);
        DeckWinRates.DeckRecord sameRateMoreBattles = record(20, 200, 120, 30, 20);
        DeckWinRates.DeckRecord fewBattles = record(30, 49, 40, 10, 5);
        DeckWinRates.DeckRecord fewPlayers = record(40, 100, 80, 9, 20);
        DeckWinRates.DeckRecord onePlayer = record(50, 100, 80, 10, 31);
        DeckWinRates winRates = new DeckWinRates(Instant.parse("2026-10-07T00:00:00Z"), null, 500,
                List.of(listedLow, listedHigh, sameRateMoreBattles, fewBattles, fewPlayers, onePlayer));

        assertEquals(List.of(sameRateMoreBattles, listedHigh, listedLow), winRates.ranked());
        assertEquals(60, listedHigh.winRatePercent());
    }
}
