package com.example.clashroyaleapi.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClanResponse(
        String tag,
        String name,
        String description,
        int clanScore,
        int members,
        List<Member> memberList
) {
    // lastSeen は "20260915T092124.000Z" 形式(UTC)。非アクティブ日数の算出に使う。
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Member(String tag, String name, String role, int trophies, int donations, String lastSeen) {
    }
}
