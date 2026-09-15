package com.example.clashroyaleapi.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * クラン内の役職。公式APIの値・表示ラベルのキー・並び順の序列が散らばらないよう1箇所にまとめる。
 */
public enum ClanRole {

    LEADER("leader", 0),
    CO_LEADER("coLeader", 1),
    ELDER("elder", 2),
    MEMBER("member", 3);

    /** 対応表にない役職(APIに新しい役職が増えた場合)は並び順の最後に置く。 */
    public static final int UNKNOWN_RANK = Integer.MAX_VALUE;

    private final String apiValue;
    private final int rank;

    ClanRole(String apiValue, int rank) {
        this.apiValue = apiValue;
        this.rank = rank;
    }

    public static Optional<ClanRole> from(String apiValue) {
        return Arrays.stream(values()).filter(role -> role.apiValue.equals(apiValue)).findFirst();
    }

    public static int rankOf(String apiValue) {
        return from(apiValue).map(ClanRole::rank).orElse(UNKNOWN_RANK);
    }

    public String apiValue() {
        return apiValue;
    }

    public int rank() {
        return rank;
    }

    public String messageKey() {
        return "role." + apiValue;
    }
}
