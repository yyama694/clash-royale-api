package com.example.clashroyaleapi.web.view;

import java.util.List;

/**
 * ランキングの動き画面の中身。
 *
 * @param comparedWith 比べた記録の日時。比べる記録が無ければ null で、変化の列と欄は出さない
 * @param players      記録に載っていた人数(公式APIが返せるのは1000人まで。シーズンの始めはそれより少ない)
 */
public record RankingReportView(TimeView recordedAt, TimeView comparedWith, int players, List<TopRow> top,
        List<LeaderChangeRow> leaderChanges, List<ClimberRow> climbers, List<DropoutRow> dropouts) {

    /**
     * @param change      順位の上下(「↑3」「↓2」「–」、前の記録に載っていなければ「新」の文言)。比べる記録が無ければ null
     * @param changeClass up / down / same / new
     * @param ratingChange 前の記録からのレーティングの増減(「+12」)。前の記録に載っていなければ null
     */
    public record TopRow(int rank, PlayerLinkView player, int rating, String change, String changeClass,
            String ratingChange, String ratingChangeClass) {
    }

    public record LeaderChangeRow(TimeView at, PlayerLinkView leader, PlayerLinkView previous) {
    }

    public record ClimberRow(PlayerLinkView player, int previousRank, int rank, String ratingGain) {
    }

    /** @param rank 今のランキング(上位1000人)にもいなければ null */
    public record DropoutRow(PlayerLinkView player, int previousRank, Integer rank) {
    }
}
