package com.example.clashroyaleapi.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClanSearchResponse(List<ClanSummary> items) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClanSummary(String tag, String name, int clanScore, int members) {
    }
}
