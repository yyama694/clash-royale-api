package com.example.clashroyaleapi.domain;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 同じ8枚を使っているトッププレイヤーを束ねたもの(デッキ画面の「人気のデッキ」)。
 * カードの並び順・形(進化・ヒーロー)・タワーユニットは区別しない。
 *
 * @param best    束ねたうち最上位の人のデッキ。表示とコピーには、この人の並び順・形・タワーユニットを使う
 * @param players 使っている人数
 */
public record PopularDeck(TopDecks.SampledDeck best, int players) {

    /**
     * @param ranked     順位の順(上位が先)
     * @param minPlayers これより少ない人数のデッキは出さない
     * @param limit      出す数
     */
    public static List<PopularDeck> rank(List<TopDecks.SampledDeck> ranked, int minPlayers, int limit) {
        Map<Set<Integer>, PopularDeck> groups = new LinkedHashMap<>();
        for (TopDecks.SampledDeck deck : ranked) {
            if (deck.cardIds().size() != Deck.SIZE) {
                continue;
            }
            groups.merge(Set.copyOf(deck.cardIds()), new PopularDeck(deck, 1),
                    (first, added) -> new PopularDeck(first.best(), first.players() + 1));
        }
        // 同じ人数なら、最上位の人の順位が高い方を先にする(LinkedHashMap は最初に出た順なので、安定ソートでそうなる)。
        return groups.values().stream()
                .filter(group -> group.players() >= minPlayers)
                .sorted(Comparator.comparingInt(PopularDeck::players).reversed())
                .limit(limit)
                .toList();
    }
}
