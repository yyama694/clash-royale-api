package com.example.clashroyaleapi.web.view;

import java.util.List;

/** クラン情報画面の「クラン対戦成績」。1位が無ければ集計の行を、firstPlaceStreak が2未満なら連続の行を出さない。 */
public record ClanWarHistoryView(List<WeekView> weeks, int firstPlaces, int firstPlaceStreak) {

    /** rank が null の週(メダル0)は、順位の欄を空ける。 */
    public record WeekView(String endedOn, Integer rank, int medals, String trophyChange, String trophyChangeClass,
            boolean firstPlace) {
    }
}
