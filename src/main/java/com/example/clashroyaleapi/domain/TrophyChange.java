package com.example.clashroyaleapi.domain;

import java.util.Optional;

/**
 * 1戦でのトロフィー(ランク戦ではレーティング)の増減。公式APIの trophyChange を、意味の分かっている対戦だけで使う。
 * 通常のトロフィー戦(type が PvP)はトロフィー、ランク戦(pathOfLegend)はレーティング。
 * イベント戦などにも trophyChange が入ることがある(+1 など)が、トロフィーではないので使わない(2026-10-04に実データで確認)。
 * ランク戦でも trophyChange が無い対戦があり、そのときは増減を出さない。
 */
public record TrophyChange(Kind kind, int amount) {

    public enum Kind {
        TROPHIES, RATING
    }

    public static Optional<TrophyChange> of(String battleType, Integer trophyChange) {
        if (trophyChange == null || trophyChange == 0) {
            return Optional.empty();
        }
        Kind kind = switch (battleType == null ? "" : battleType) {
            case "PvP" -> Kind.TROPHIES;
            case "pathOfLegend" -> Kind.RATING;
            default -> null;
        };
        return kind == null ? Optional.empty() : Optional.of(new TrophyChange(kind, trophyChange));
    }

    public boolean gained() {
        return amount > 0;
    }
}
