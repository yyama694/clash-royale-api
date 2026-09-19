package com.example.clashroyaleapi.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** クラン対戦の履歴(/clans/{tag}/riverracelog)。巡回でプレイヤーとクランを集めるのに使う部分だけを受け取る。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RiverRaceLogResponse(List<RiverRace> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RiverRace(List<Standing> standings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Standing(Clan clan) {
    }

    // participants には対戦に参加した元メンバーも含まれるため、メンバー一覧(最大50人)より多くの人数が取れる。
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Clan(String tag, String name, List<Participant> participants) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Participant(String tag, String name) {
    }
}
