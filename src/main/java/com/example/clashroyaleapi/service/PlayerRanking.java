package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;

import java.time.YearMonth;
import java.util.List;

/**
 * 個人ランキングと、それがどのシーズンの順位か。
 * 「今シーズンにまだ誰もいない」と「取得に失敗した」は画面の文言を分けるため、空のときも理由を持たせる。
 *
 * @param finishedSeason status が {@link Status#FINISHED_SEASON} のときだけ、そのシーズン
 */
public record PlayerRanking(List<PlayerRankingResponse.RankedPlayer> players, Status status,
        YearMonth finishedSeason) {

    public enum Status {
        CURRENT_SEASON,
        /** 今シーズンの順位だが、シーズンが始まったばかりで、まだ画面の件数に満たない。 */
        NEW_SEASON,
        /** 今シーズンのランキングが空なので、終わったシーズンの最終順位を代わりに返した。 */
        FINISHED_SEASON,
        /** 今シーズンのランキングにまだ誰もいない(新シーズンの開始直後や、プレイヤーの少ない国)。 */
        EMPTY,
        UNAVAILABLE
    }

    public static PlayerRanking current(List<PlayerRankingResponse.RankedPlayer> players) {
        return new PlayerRanking(players, Status.CURRENT_SEASON, null);
    }

    public static PlayerRanking newSeason(List<PlayerRankingResponse.RankedPlayer> players) {
        return new PlayerRanking(players, Status.NEW_SEASON, null);
    }

    public static PlayerRanking finishedSeason(List<PlayerRankingResponse.RankedPlayer> players, YearMonth season) {
        return new PlayerRanking(players, Status.FINISHED_SEASON, season);
    }

    public static PlayerRanking empty() {
        return new PlayerRanking(List.of(), Status.EMPTY, null);
    }

    public static PlayerRanking unavailable() {
        return new PlayerRanking(List.of(), Status.UNAVAILABLE, null);
    }
}
