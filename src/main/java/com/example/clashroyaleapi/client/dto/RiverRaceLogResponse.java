package com.example.clashroyaleapi.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** クラン対戦の履歴(/clans/{tag}/riverracelog)。巡回(参加者の収集)と、クラン情報画面の「クラン対戦成績」で使う。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RiverRaceLogResponse(List<RiverRace> items) {

    /** createdDate はその週の対戦が終わって結果が確定した日時。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RiverRace(String createdDate, List<Standing> standings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Standing(int rank, int trophyChange, Clan clan) {
    }

    // participants には対戦に参加した元メンバーも含まれるため、メンバー一覧(最大50人)より多くの人数が取れる。
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Clan(String tag, String name, int fame, List<Participant> participants) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Participant(String tag, String name) {
    }
}
