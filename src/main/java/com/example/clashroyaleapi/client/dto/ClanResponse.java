package com.example.clashroyaleapi.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** location はクランランキングと同じ形なので、型を共用している。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClanResponse(
        String tag,
        String name,
        String description,
        int clanScore,
        int clanWarTrophies,
        int members,
        List<Member> memberList,
        String type,
        int requiredTrophies,
        int donationsPerWeek,
        ClanRankingResponse.Location location
) {
    // lastSeen は "20260915T092124.000Z" 形式(UTC)。非アクティブ日数の算出に使う。
    // clanRank はゲーム内のクランの順位(1始まり)。ランク戦のレーティングが高い人が先で、その次がトロフィー順
    // (2026-10-11に上位プレイヤーの多い3クランで確かめた)。メンバー一覧はこの順で返る。
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Member(String tag, String name, String role, int trophies, int donations, String lastSeen,
            int clanRank) {
    }
}
