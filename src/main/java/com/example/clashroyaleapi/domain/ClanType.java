package com.example.clashroyaleapi.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * クランのタイプ(公式APIの type)。ゲーム内の表記は「参加自由」「招待のみ」「参加不可」(texts.csv の TID_ALLIANCE_TYPE_*)。
 */
public enum ClanType {

    OPEN("open"),
    INVITE_ONLY("inviteOnly"),
    CLOSED("closed");

    /** 1つのクランに入れる人数の上限。 */
    public static final int MAX_MEMBERS = 50;

    private final String apiValue;

    ClanType(String apiValue) {
        this.apiValue = apiValue;
    }

    public static Optional<ClanType> from(String apiValue) {
        return Arrays.stream(values()).filter(type -> type.apiValue.equals(apiValue)).findFirst();
    }

    public String messageKey() {
        return "clan.type." + apiValue;
    }

    /** あと何人入れるか。参加不可のクランは空きがあっても募集していないので0。 */
    public int openSlots(int members) {
        return this == CLOSED ? 0 : Math.max(0, MAX_MEMBERS - members);
    }
}
