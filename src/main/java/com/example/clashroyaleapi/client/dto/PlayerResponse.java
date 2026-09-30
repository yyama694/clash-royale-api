package com.example.clashroyaleapi.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * currentDeck のカードは battlelog のカードと同じ形なので、型を共用している。
 * currentWinLoseStreak(連勝・連敗)は、通常のトロフィー戦だけを数えているとみられ実態と合わないので受け取らない
 * (対戦履歴から数える。WinLoseStreak 参照)。
 * expLevel(旧キングレベル)は2026-05-26のXP廃止で更新されなくなり、以後に作られたアカウントは1のままなので受け取らない。
 * 今のゲームが表示するのは kingTowerLevel(カードの強化状況で決まる、最大16)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayerResponse(
        String tag,
        String name,
        int kingTowerLevel,
        int trophies,
        int bestTrophies,
        int wins,
        int losses,
        int threeCrownWins,
        ClanRef clan,
        List<BattleLogEntry.Card> currentDeck,
        List<BattleLogEntry.Card> currentDeckSupportCards,
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
     * 所持カード。公式APIは持っているカードだけを返す(育成途中のプレイヤーは8〜64枚など。2026-09-27・09-30に確認)。
     * 以前は「ほぼ全カードを含む」と考えていたが、全カードを持つ上位勢だけを見た誤りだった。
     * countが0でもlevel==maxLevelのことがある(手持ちを使い切って最大レベルにした場合)。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OwnedCard(int id, String name, int level, int maxLevel, int count, String rarity) {
    }
}
