package com.example.clashroyaleapi.web.view;

/**
 * プロ・クリエイターの肩書き。一覧とプレイヤー情報画面で同じ形で出す。
 *
 * @param name      活動名
 * @param kindCode  pro / creator
 * @param kindLabel 「プロ」「クリエイター」
 * @param team      所属チーム。無ければ null
 */
public record ProBadgeView(String name, String kindCode, String kindLabel, String team, String countryName) {
}
