package com.example.clashroyaleapi.web.view;

/**
 * 名前検索の候補に添える、今のプロフィール。同じ名前の人を見分けるために使う。
 * kingTowerLevel・clanName・worldRank は無ければ null(worldRank はランク戦で世界の順位が付いた人だけ)。
 */
public record PlayerProfileView(int trophies, Integer kingTowerLevel, String clanName, Integer worldRank) {
}
