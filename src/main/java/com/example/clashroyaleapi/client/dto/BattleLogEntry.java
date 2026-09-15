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
    public record Participant(String tag, String name, int crowns, List<Card> cards, List<Card> supportCards) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Card(String name, int level, IconUrls iconUrls) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IconUrls(String medium) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GameMode(String name) {
    }
}
