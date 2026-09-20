package com.example.clashroyaleapi.web;

/** お気に入りの対象種別。Cookie名を兼ねる。 */
public enum FavoriteKind {

    PLAYER("fav_players"),
    CLAN("fav_clans");

    private final String cookieName;

    FavoriteKind(String cookieName) {
        this.cookieName = cookieName;
    }

    public String cookieName() {
        return cookieName;
    }
}
