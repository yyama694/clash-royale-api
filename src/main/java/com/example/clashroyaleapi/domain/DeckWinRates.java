package com.example.clashroyaleapi.domain;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

/**
 * ランク戦の世界上位のプレイヤーどうしの対戦から数えた、デッキごとの勝率(勝率の高いデッキ画面)。
 * 8枚が同じなら同じデッキとし、並び順・形(進化・ヒーロー)・タワーユニットは区別しない(人気のデッキ画面と同じ)。
 * 集め方は {@link DeckWinRateTally}。
 *
 * @param finishedSeason 前のシーズンの最終順位の上位で集めたときのシーズン。今シーズンのランキングで集めたときは null
 * @param battles        数えた対戦の数(同じデッキどうしの対戦を除く)
 * @param decks          数えたすべてのデッキ。画面に出すのは {@link #ranked()} の条件を満たすものだけ
 */
public record DeckWinRates(Instant collectedAt, YearMonth finishedSeason, int battles, List<DeckRecord> decks) {

    /*
     * 2026-10-18の上位1000人(今シーズンの両者とも上位の対戦11,750戦)で、この条件を満たすデッキは30種類だった。
     * 使う人が少ないデッキは1人の対戦が6〜8割を占め、勝率70〜79%で上位に来ていた(その人の腕の勝率になる)。
     */
    public static final int MIN_BATTLES = 50;
    public static final int MIN_PLAYERS = 10;
    public static final int MAX_PLAYER_SHARE_PERCENT = 30;

    /**
     * @param cardIds          使った人のうち最上位の人の、最も新しい対戦の並び順(forms・towerTroopIdも同じ対戦のもの)
     * @param towerTroopId     タワーユニットを返さない対戦では null
     * @param top              使った人のうち最上位の人
     * @param wins             引き分けは勝ちに数えない(分母には入れる。プレイヤー情報画面の勝率と同じ)
     * @param maxPlayerBattles 1人で最も多く使った人の対戦数
     */
    public record DeckRecord(List<Integer> cardIds, List<CardForm> forms, Integer towerTroopId, TopPlayer top,
                             int battles, int wins, int players, int maxPlayerBattles) {

        public int winRatePercent() {
            return Math.round(100f * wins / battles);
        }

        public CardForm formAt(int index) {
            return index < forms.size() ? forms.get(index) : CardForm.NORMAL;
        }

        boolean listed() {
            return battles >= MIN_BATTLES && players >= MIN_PLAYERS
                    && maxPlayerBattles * 100 <= battles * MAX_PLAYER_SHARE_PERCENT;
        }

        double winRate() {
            return (double) wins / battles;
        }
    }

    public record TopPlayer(String tag, String name, int rank) {
    }

    /** 画面に出すデッキを、勝率の高い順に。同じ勝率なら対戦の多い順。 */
    public List<DeckRecord> ranked() {
        return decks.stream()
                .filter(DeckRecord::listed)
                .sorted(Comparator.comparingDouble(DeckRecord::winRate).reversed()
                        .thenComparing(Comparator.comparingInt(DeckRecord::battles).reversed()))
                .toList();
    }
}
