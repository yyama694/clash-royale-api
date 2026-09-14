package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 直近の対戦履歴(battlelog)から、勝敗数とカードごとの使用実績を集計する。
 * battlelogは常に"team[0]"が検索対象プレイヤー自身を表す(公式APIの仕様)という前提で計算する。
 */
public record PlayerBattleStats(int total, int wins, int losses, int draws, List<CardPerformance> favoriteCards,
        List<CardPerformance> weakCards) {

    // カードごとの得意/苦手を判定する際、使用回数がこれ未満のカードはノイズとして除外する。
    private static final int MIN_USES_FOR_RANKING = 2;
    private static final int RANKING_SIZE = 3;

    public record CardPerformance(String cardName, String iconUrl, int uses, int wins, int winRatePercent) {
    }

    public static PlayerBattleStats from(List<BattleLogEntry> battleLog) {
        int wins = 0;
        int losses = 0;
        int draws = 0;
        Map<String, CardTally> tallies = new LinkedHashMap<>();

        for (BattleLogEntry battle : battleLog) {
            BattleLogEntry.Participant self = battle.team().get(0);
            BattleLogEntry.Participant opponent = battle.opponent().get(0);
            boolean won = self.crowns() > opponent.crowns();
            boolean lost = self.crowns() < opponent.crowns();
            if (won) {
                wins++;
            } else if (lost) {
                losses++;
            } else {
                draws++;
            }
            for (BattleLogEntry.Card card : self.cards()) {
                CardTally tally = tallies.computeIfAbsent(card.name(), k -> new CardTally(cardIconUrl(card)));
                tally.uses++;
                if (won) {
                    tally.wins++;
                }
            }
        }

        List<CardPerformance> ranked = tallies.entrySet().stream()
                .filter(e -> e.getValue().uses >= MIN_USES_FOR_RANKING)
                .map(e -> toPerformance(e.getKey(), e.getValue()))
                .toList();
        // 使用回数がMIN_USES_FOR_RANKING未満のカードしかない場合(対戦数が少ない等)は、全カードを対象にフォールバックする。
        if (ranked.isEmpty()) {
            ranked = tallies.entrySet().stream()
                    .map(e -> toPerformance(e.getKey(), e.getValue()))
                    .toList();
        }

        List<CardPerformance> favorite = ranked.stream()
                .sorted(Comparator.comparingInt(CardPerformance::winRatePercent).reversed()
                        .thenComparing(Comparator.comparingInt(CardPerformance::uses).reversed()))
                .limit(RANKING_SIZE)
                .toList();
        List<CardPerformance> weak = ranked.stream()
                .sorted(Comparator.comparingInt(CardPerformance::winRatePercent)
                        .thenComparing(Comparator.comparingInt(CardPerformance::uses).reversed()))
                .limit(RANKING_SIZE)
                .toList();

        return new PlayerBattleStats(wins + losses + draws, wins, losses, draws, favorite, weak);
    }

    private static String cardIconUrl(BattleLogEntry.Card card) {
        return card.iconUrls() != null ? card.iconUrls().medium() : null;
    }

    private static CardPerformance toPerformance(String name, CardTally tally) {
        int winRate = Math.round(100f * tally.wins / tally.uses);
        return new CardPerformance(name, tally.iconUrl, tally.uses, tally.wins, winRate);
    }

    private static final class CardTally {
        private final String iconUrl;
        private int uses;
        private int wins;

        private CardTally(String iconUrl) {
            this.iconUrl = iconUrl;
        }
    }
}
