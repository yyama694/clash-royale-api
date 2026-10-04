package com.example.clashroyaleapi.web.view;

/**
 * クラン情報画面の参加条件。typeLabel は公式APIのタイプが未知の値なら null、locationName は所在地が無ければ null。
 * openSlots は募集しているときの空きの人数で、満員・参加不可・タイプ不明なら0。
 */
public record ClanJoinView(String typeLabel, int requiredTrophies, String locationName, int donationsPerWeek,
        int openSlots) {

    public boolean recruiting() {
        return openSlots > 0;
    }
}
