package com.example.clashroyaleapi.domain;

/**
 * ランキングの集計範囲。公式APIのlocationIdと、表示ラベルのキーを対応付ける。
 * 国を増やす場合はここに定数を足す。
 */
public enum RankingScope {

    GLOBAL("global", "global"),
    // 57000122 = Japan。/locations が返すIDで、固定値。
    LOCAL("local", "57000122");

    private final String code;
    private final String locationId;

    RankingScope(String code, String locationId) {
        this.code = code;
        this.locationId = locationId;
    }

    public String code() {
        return code;
    }

    public String locationId() {
        return locationId;
    }
}
