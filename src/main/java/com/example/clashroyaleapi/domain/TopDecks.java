package com.example.clashroyaleapi.domain;

import java.time.Instant;
import java.util.List;

/**
 * ランク戦の世界上位プレイヤーが、集計した時点でゲーム内にセットしていたデッキ(currentDeck)の標本。
 * 対戦で実際に使った回数ではない点に注意(公式APIは上位プレイヤーの対戦を集計する手段を持たない)。
 */
public record TopDecks(Instant collectedAt, List<SampledDeck> decks) {

    /** towerTroopId はタワーユニットを返さないプレイヤーでは null。 */
    public record SampledDeck(List<Integer> cardIds, Integer towerTroopId) {
    }
}
