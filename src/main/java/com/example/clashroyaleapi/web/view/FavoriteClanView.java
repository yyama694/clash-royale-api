package com.example.clashroyaleapi.web.view;

/** clanScore・members は found() のときだけ値を持つ。 */
public record FavoriteClanView(FavoriteRowStatus status, String pathTag, String tag, String name,
        Integer clanScore, Integer members) {

    public boolean found() {
        return status == FavoriteRowStatus.FOUND;
    }

    public boolean notFound() {
        return status == FavoriteRowStatus.NOT_FOUND;
    }
}
