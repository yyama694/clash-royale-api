package com.example.clashroyaleapi.web.view;

/** 1戦でのトロフィー(ランク戦ではレーティング)の増減。amount は符号付きで整形済み(「+30」「-28」)。 */
public record TrophyChangeView(String label, String amount, boolean gained) {
}
