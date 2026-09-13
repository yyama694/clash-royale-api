package com.example.clashroyaleapi.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayerResponse(
        String tag,
        String name,
        int expLevel,
        int trophies,
        int bestTrophies,
        int wins,
        int losses,
        int threeCrownWins,
        ClanRef clan
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClanRef(String tag, String name) {
    }
}
