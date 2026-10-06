package com.example.clashroyaleapi.domain;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

/**
 * ランク戦の世界上位プレイヤーが、直近のランク戦で使ったデッキの標本(1人1デッキ)。
 * 使った回数は数えていない点に注意(公式APIの対戦履歴は1人25戦分しか返さない)。
 *
 * @param finishedSeason 前のシーズンの最終順位の上位で集めたときのシーズン。今シーズンのランキングで集めたときは null
 */
public record TopDecks(Instant collectedAt, List<SampledDeck> decks, YearMonth finishedSeason) {

    public TopDecks(Instant collectedAt, List<SampledDeck> decks) {
        this(collectedAt, decks, null);
    }

    /**
     * towerTroopId はタワーユニットを返さないプレイヤーでは null。
     * player は誰のデッキか。2026-09-27より前の形式の集計ファイルには無いので null になる。
     */
    public record SampledDeck(List<Integer> cardIds, Integer towerTroopId, Player player) {

        public SampledDeck(List<Integer> cardIds, Integer towerTroopId) {
            this(cardIds, towerTroopId, null);
        }

        public boolean contains(int cardId) {
            return cardIds.contains(cardId) || Integer.valueOf(cardId).equals(towerTroopId);
        }
    }

    /**
     * 集計した時点のランキングの情報と、その対戦でのカードのレベル・形。
     *
     * @param levels     cardIds と同じ順の、ゲーム内表記のレベル
     * @param towerLevel タワーユニットのゲーム内表記のレベル。タワーユニットが無ければ null
     * @param forms      cardIds と同じ順の、その対戦で使った形。2026-10-03より前の形式の集計ファイルには無いので空になる
     */
    public record Player(String tag, String name, int rank, int rating, List<Integer> levels, Integer towerLevel,
            List<CardForm> forms) {

        public Player(String tag, String name, int rank, int rating, List<Integer> levels, Integer towerLevel) {
            this(tag, name, rank, rating, levels, towerLevel, List.of());
        }

        public CardForm formAt(int index) {
            return index < forms.size() ? forms.get(index) : CardForm.NORMAL;
        }
    }
}
