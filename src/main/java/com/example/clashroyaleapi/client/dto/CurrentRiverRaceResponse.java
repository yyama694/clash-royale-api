package com.example.clashroyaleapi.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 今週のクラン対戦(/clans/{tag}/currentriverrace)。参加状況の表示に使う部分だけを受け取る。
 * periodType は "training"(攻撃の無い日)、"warDay"、"colosseum"(シーズン最終週)のいずれか(2026-09-26に実データで確認)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CurrentRiverRaceResponse(String periodType, Clan clan) {

    // participants には、今週の対戦に参加してからクランを抜けた元メンバーも含まれる。
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Clan(String tag, List<Participant> participants) {
    }

    /** decksUsed は今週の累計、decksUsedToday はゲーム内の今日の分(1日最大4)。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Participant(String tag, String name, int decksUsed, int decksUsedToday) {
    }
}
