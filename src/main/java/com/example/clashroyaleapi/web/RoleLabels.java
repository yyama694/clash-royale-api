package com.example.clashroyaleapi.web;

import java.util.Locale;
import java.util.Map;

/**
 * Clash Royale公式APIが返すクラン内役職名(leader/coLeader/elder/memberのcamelCase)を、
 * アクセス言語ごとの表示用ラベルに変換する。
 * 日本語はゲーム内で実際に使われている表記(直訳ではない)、それ以外の言語は
 * 英語の正式な表記(Leader/Co-leader/Elder/Member)にフォールバックする。
 * 言語を追加する場合は各エントリのMapに言語コードを足すだけでよい。
 */
public final class RoleLabels {

    private static final String FALLBACK_LANGUAGE = "en";

    private static final Map<String, Map<String, String>> LABELS = Map.of(
            "leader", Map.of(
                    "ja", "リーダー",
                    FALLBACK_LANGUAGE, "Leader"
            ),
            "coLeader", Map.of(
                    "ja", "サブリーダー",
                    FALLBACK_LANGUAGE, "Co-leader"
            ),
            "elder", Map.of(
                    "ja", "長老",
                    FALLBACK_LANGUAGE, "Elder"
            ),
            "member", Map.of(
                    "ja", "メンバー",
                    FALLBACK_LANGUAGE, "Member"
            )
    );

    private RoleLabels() {
    }

    public static String label(String rawRole, Locale locale) {
        if (rawRole == null || rawRole.isBlank()) {
            return "-";
        }
        Map<String, String> translations = LABELS.get(rawRole);
        if (translations == null) {
            return rawRole;
        }
        String language = locale == null ? FALLBACK_LANGUAGE : locale.getLanguage().toLowerCase(Locale.ROOT);
        return translations.getOrDefault(language, translations.get(FALLBACK_LANGUAGE));
    }
}
