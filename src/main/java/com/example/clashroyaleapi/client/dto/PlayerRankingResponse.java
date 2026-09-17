package com.example.clashroyaleapi.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayerRankingResponse(List<RankedPlayer> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RankedPlayer(String tag, String name, int expLevel, int eloRating, int rank, Clan clan) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Clan(String tag, String name) {
    }
}
