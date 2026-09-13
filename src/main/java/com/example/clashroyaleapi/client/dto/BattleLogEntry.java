package com.example.clashroyaleapi.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BattleLogEntry(
        String type,
        String battleTime,
        GameMode gameMode,
        List<Participant> team,
        List<Participant> opponent
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Participant(String tag, String name, int crowns) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GameMode(String name) {
    }
}
