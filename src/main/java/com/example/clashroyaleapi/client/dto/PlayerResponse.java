package com.example.clashroyaleapi.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * currentDeck のカードは battlelog のカードと同じ形なので、型を共用している。
 * currentWinLoseStreak は連勝なら正、連敗なら負の数(2026-09-19に実データと対戦履歴を突き合わせて確認)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayerResponse(
        String tag,
        String name,
        int expLevel,
        int trophies,
        int bestTrophies,
        int wins,
        int losses,
        int threeCrownWins,
        ClanRef clan,
        List<BattleLogEntry.Card> currentDeck,
        List<BattleLogEntry.Card> currentDeckSupportCards,
        Integer currentWinLoseStreak,
        RankedSeasonResult currentPathOfLegendSeasonResult,
        RankedSeasonResult bestPathOfLegendSeasonResult,
        List<OwnedCard> cards
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClanRef(String tag, String name) {
    }

    /**
     * ランク戦(旧パス・オブ・レジェンド)のシーズン成績。trophies はレーティング。
     * 上位の順位に入っていないプレイヤーは rank が null、trophies が 0 になる。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RankedSeasonResult(Integer leagueNumber, Integer trophies, Integer rank) {
    }

    /**
     * 所持カード。公式APIはレアリティを問わずほぼ全カードを含み、countが0でも
     * level==maxLevelのことがある(手持ちを使い切って最大レベルにした場合)。
     * そのため「所持しているか」の判定にはcountを使わず、level/maxLevelだけを見る(CardCollection参照)。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OwnedCard(int id, String name, int level, int maxLevel, int count, String rarity) {
    }
}
