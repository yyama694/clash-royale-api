package com.example.clashroyaleapi.web;

import java.util.Map;

/**
 * Clash Royale公式APIが返すクラン内役職名(英語)を、アクセス言語が日本語の場合のみ
 * ゲーム内で実際に使われている日本語表記に変換する(直訳ではなく公式ローカライズ名)。
 * 未知の役職名は英語名のままフォールバックする。
 */
public final class RoleLabels {

    private static final Map<String, String> JAPANESE_LABELS = Map.of(
            "leader", "リーダー",
            "coLeader", "バイスリーダー",
            "elder", "長老",
            "member", "メンバー"
    );

    private RoleLabels() {
    }

    public static String label(String rawRole, boolean useJapanese) {
        if (rawRole == null || rawRole.isBlank()) {
            return "-";
        }
        if (!useJapanese) {
            return rawRole;
        }
        return JAPANESE_LABELS.getOrDefault(rawRole, rawRole);
    }
}
