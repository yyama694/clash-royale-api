package com.example.clashroyaleapi.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClanRankingResponse(List<RankedClan> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RankedClan(String tag, String name, int rank, int clanScore, int members, Location location) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Location(String name, String countryCode) {
    }
}
