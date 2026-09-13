package com.example.clashroyaleapi.web;

import java.util.Map;

/**
 * Clash Royale公式APIが返すgameMode名(英語)を、日本語話者にも外国語話者にも
 * わかりやすいよう「日本語ラベル (英語名)」の形式に変換する。
 * 未知のモード名は英語名をそのまま表示する(フォールバック)。
 */
public final class GameModeLabels {

    private static final Map<String, String> JAPANESE_LABELS = Map.ofEntries(
            Map.entry("Ladder", "ランク戦"),
            Map.entry("Ranked1v1", "ランク戦(1vs1)"),
            Map.entry("PvP", "対戦"),
            Map.entry("Tournament", "トーナメント"),
            Map.entry("Challenge", "チャレンジ"),
            Map.entry("ChallengeDeckWarmup", "チャレンジ(デッキ練習)"),
            Map.entry("ClanWarWarDay", "クラン対抗戦(ウォーデイ)"),
            Map.entry("ClanWarCollectionDay", "クラン対抗戦(収集日)"),
            Map.entry("CasualDuel1v1", "カジュアル対戦"),
            Map.entry("CasualDuel2v2", "カジュアル対戦(2vs2)"),
            Map.entry("Deathmatch", "デスマッチ"),
            Map.entry("TripleDraft", "トリプルドラフト"),
            Map.entry("2v2", "2vs2対戦"),
            Map.entry("PathOfLegend", "パス・オブ・レジェンド"),
            Map.entry("Friendly", "フレンドバトル")
    );

    private GameModeLabels() {
    }

    public static String label(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "-";
        }
        String japanese = JAPANESE_LABELS.get(rawName);
        return japanese == null ? rawName : japanese + " (" + rawName + ")";
    }
}
