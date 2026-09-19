package com.example.clashroyaleapi.web.view;

/** clanWarTrophies は上位クランだけ個別取得するため、対象外の行はnull。 */
public record ClanRankingRowView(int rank, String pathTag, String tag, String name, int clanScore, int members,
        String locationName, Integer clanWarTrophies) {
}
