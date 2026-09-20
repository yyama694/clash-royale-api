package com.example.clashroyaleapi.web.view;

/** trophies・rankedRating・clanName・clanPathTag は found() のときだけ値を持つ。 */
public record FavoritePlayerView(FavoriteRowStatus status, String pathTag, String tag, String name,
        Integer trophies, Integer rankedRating, String clanName, String clanPathTag) {

    public boolean found() {
        return status == FavoriteRowStatus.FOUND;
    }

    public boolean notFound() {
        return status == FavoriteRowStatus.NOT_FOUND;
    }
}
