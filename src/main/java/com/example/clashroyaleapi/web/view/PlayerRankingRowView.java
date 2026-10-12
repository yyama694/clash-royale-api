package com.example.clashroyaleapi.web.view;

/** change は24時間の順位の変化。比べる記録が無い表(国別・トップページ・終わったシーズン)では null。 */
public record PlayerRankingRowView(int rank, String pathTag, String tag, String name, int eloRating,
        String clanName, String clanPathTag, RankChangeView change) {
}
