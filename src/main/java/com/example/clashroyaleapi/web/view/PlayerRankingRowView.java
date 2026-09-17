package com.example.clashroyaleapi.web.view;

public record PlayerRankingRowView(int rank, String pathTag, String tag, String name, int expLevel, int eloRating,
        String clanName, String clanPathTag) {
}
