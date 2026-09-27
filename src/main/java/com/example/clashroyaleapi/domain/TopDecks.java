package com.example.clashroyaleapi.domain;

import java.time.Instant;
import java.util.List;

/**
 * ランク戦の世界上位プレイヤーが、直近のランク戦で使ったデッキの標本(1人1デッキ)。
 * 使った回数は数えていない点に注意(公式APIの対戦履歴は1人25戦分しか返さない)。
 */
public record TopDecks(Instant collectedAt, List<SampledDeck> decks) {

    /** towerTroopId はタワーユニットを返さないプレイヤーでは null。 */
    public record SampledDeck(List<Integer> cardIds, Integer towerTroopId) {
    }
}
