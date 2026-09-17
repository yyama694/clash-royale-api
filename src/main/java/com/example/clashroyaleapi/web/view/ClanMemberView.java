package com.example.clashroyaleapi.web.view;

/**
 * inactiveDays は lastSeen が取得できなかった場合 null。
 * lastSeenIso は &lt;time datetime&gt; 用で、解釈できなかった場合は null。
 */
public record ClanMemberView(String pathTag, String name, String role, int trophies, int donations,
        Long inactiveDays, boolean longInactive, String lastSeenIso) {
}
