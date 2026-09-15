package com.example.clashroyaleapi.web.view;

/** inactiveDays は lastSeen が取得できなかった場合 null。 */
public record ClanMemberView(String pathTag, String name, String role, int trophies, int donations,
        Long inactiveDays) {
}
