package com.example.clashroyaleapi.domain;

import java.util.OptionalInt;

/**
 * ランク戦のリーグ。公式APIの leagueNumber(対戦履歴の各対戦と、プレイヤーの今シーズン・前シーズンの成績)。
 * 2026年7月に修羅の道I〜IIIがなくなり、1=マスターI 〜 7=天界 の7段階になった(レーティングが付くのは7だけ)。
 * それより前の10段階(1=修羅の道I 〜 10=天界)の番号が、プレイヤーの過去シーズンの最高(bestPathOfLegendSeasonResult)には残っていて、
 * 今の番号と区別できないので、そちらには使わない(2026-10-14に実データとユーザーのゲーム画面で確認)。
 */
public final class RankedLeague {

    public static final int TOP = 7;
    /** ランク戦に入れるトロフィー数。公式APIのトロフィーはここで止まる。 */
    public static final int ENTRY_TROPHIES = 14_000;

    private RankedLeague() {
    }

    /** 今の7段階の番号として意味のある値なら、その番号。 */
    public static OptionalInt of(Integer leagueNumber) {
        return leagueNumber != null && leagueNumber >= 1 && leagueNumber <= TOP
                ? OptionalInt.of(leagueNumber) : OptionalInt.empty();
    }

    /**
     * プレイヤーの今シーズンのリーグとして出してよい番号。
     * ランク戦に入れない人にも公式APIは1を返すので、1はトロフィーが入れる数に届いている人だけ
     * (前のシーズンの成績で入れる人もいるが、見分けられないので出さない)。
     */
    public static OptionalInt currentOf(Integer leagueNumber, int trophies) {
        OptionalInt league = of(leagueNumber);
        if (league.isPresent() && league.getAsInt() == 1 && trophies < ENTRY_TROPHIES) {
            return OptionalInt.empty();
        }
        return league;
    }
}
