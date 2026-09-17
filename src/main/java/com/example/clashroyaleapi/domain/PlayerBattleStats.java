package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 直近の対戦履歴(battlelog)から、勝敗数と「対戦相手が使用したカードに対する自分の勝率」を集計する。
 * 得意カード=相手がそのカードを使った対戦での勝率が高いカード、苦手カード=勝率が低いカード、という定義。
 */
public record PlayerBattleStats(int total, int wins, int losses, int draws, List<CardPerformance> favoriteCards,
        List<CardPerformance> weakCards) {

    // 使用回数がこれ未満のカードはノイズとして除外する。5回はユーザー指定の仕様値のため、
    // Wilson score で少数回のカードの順位が下がるからといって下げないこと。
    private static final int MIN_USES_FOR_RANKING = 5;
    private static final int MAX_RANKING_SIZE = 3;
    // Wilson score interval の z 値。1.96 は95%信頼区間に対応する。
    private static final double Z = 1.96;

    public record CardPerformance(String cardName, String iconUrl, int uses, int wins, int winRatePercent,
            double lowerBound, double upperBound) {
    }

    public static PlayerBattleStats from(List<BattleLogEntry> battleLog) {
        int wins = 0;
        int losses = 0;
        int draws = 0;
        Map<String, CardTally> tallies = new LinkedHashMap<>();

        for (BattleLogEntry battle : battleLog) {
            if (isIncomplete(battle)) {
                continue;
            }
            BattleResult result = BattleResult.of(crownsOf(battle.team()), crownsOf(battle.opponent()));
            switch (result) {
                case WIN -> wins++;
                case LOSE -> losses++;
                case DRAW -> draws++;
            }
            // 2v2では相手が2人いる。片方だけを見ると相方のデッキ8枚が丸ごと集計から漏れる。
            for (BattleLogEntry.Participant opponent : battle.opponent()) {
                tally(tallies, opponent, result);
            }
        }

        List<CardPerformance> ranked = rank(tallies);
        // 「得意」と「苦手」を両方出すには最低2枚必要。母数が少ないときは無理に3枚並べない。
        int size = Math.min(MAX_RANKING_SIZE, ranked.size() / 2);

        // 単純な勝率降順だと、1〜2回しか当たっていないカードが勝率100%で上位を独占する。
        // Wilson score interval の下限で並べることで、試行回数が少ないカードは自動的に順位が下がる。
        List<CardPerformance> favorite = ranked.stream()
                .sorted(Comparator.comparingDouble(CardPerformance::lowerBound).reversed()
                        .thenComparing(Comparator.comparingInt(CardPerformance::uses).reversed()))
                .limit(size)
                .toList();

        // 同じカードが得意にも苦手にも並ぶと画面として破綻するため、得意に選ばれた分は除外する。
        Set<String> alreadyRanked = favorite.stream().map(CardPerformance::cardName).collect(Collectors.toSet());
        List<CardPerformance> weak = ranked.stream()
                .filter(card -> !alreadyRanked.contains(card.cardName()))
                .sorted(Comparator.comparingDouble(CardPerformance::upperBound)
                        .thenComparing(Comparator.comparingInt(CardPerformance::uses).reversed()))
                .limit(size)
                .toList();

        return new PlayerBattleStats(wins + losses + draws, wins, losses, draws, favorite, weak);
    }

    private static void tally(Map<String, CardTally> tallies, BattleLogEntry.Participant opponent,
            BattleResult result) {
        if (opponent.cards() == null) {
            return;
        }
        for (BattleLogEntry.Card card : opponent.cards()) {
            if (card == null || card.name() == null) {
                continue;
            }
            CardTally cardTally = tallies.computeIfAbsent(card.name(), key -> new CardTally(iconUrlOf(card)));
            cardTally.uses++;
            if (result == BattleResult.WIN) {
                cardTally.wins++;
            }
        }
    }

    /**
     * 最低使用回数に届くカードが無ければ、得意/苦手は出さない(画面は「分析できません」と表示する)。
     * 以前は全カードにフォールバックしていたが、対戦1件だと相手の8枚がすべて同じ勝敗になり、
     * 勝率100%のカードが「苦手」に並ぶ矛盾した表示になった。
     */
    private static List<CardPerformance> rank(Map<String, CardTally> tallies) {
        return tallies.entrySet().stream()
                .filter(entry -> entry.getValue().uses >= MIN_USES_FOR_RANKING)
                .map(entry -> toPerformance(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static boolean isIncomplete(BattleLogEntry battle) {
        return battle == null
                || battle.team() == null || battle.team().isEmpty()
                || battle.opponent() == null || battle.opponent().isEmpty();
    }

    // 2v2ではチームの各メンバーに同じクラウン数が入るが、仕様として保証されていないため最大値を取る。
    private static int crownsOf(List<BattleLogEntry.Participant> side) {
        return side.stream().mapToInt(BattleLogEntry.Participant::crowns).max().orElse(0);
    }

    private static String iconUrlOf(BattleLogEntry.Card card) {
        return card.iconUrls() != null ? card.iconUrls().medium() : null;
    }

    private static CardPerformance toPerformance(String name, CardTally tally) {
        int winRate = Math.round(100f * tally.wins / tally.uses);
        return new CardPerformance(name, tally.iconUrl, tally.uses, tally.wins, winRate,
                wilsonBound(tally.wins, tally.uses, -1), wilsonBound(tally.wins, tally.uses, 1));
    }

    /**
     * Wilson score interval(母比率の信頼区間)の下限(sign=-1)と上限(sign=+1)。
     * 試行回数が少ないほど区間が広がるため、下限で並べれば少数回の全勝が、上限で並べれば少数回の全敗が、
     * それぞれ自動的に順位を下げる。相手デッキ8枚に同じ勝敗を記録する集計方式なので試行は厳密には
     * 独立ではないが、試行回数による補正の目安としては十分機能する。
     */
    private static double wilsonBound(int wins, int uses, int sign) {
        if (uses == 0) {
            return 0;
        }
        double n = uses;
        double p = wins / n;
        double denominator = 1 + Z * Z / n;
        double centre = p + Z * Z / (2 * n);
        double margin = Z * Math.sqrt(p * (1 - p) / n + Z * Z / (4 * n * n));
        return (centre + sign * margin) / denominator;
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
