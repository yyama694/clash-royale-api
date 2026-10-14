package com.example.clashroyaleapi.domain;

import java.util.Optional;

/**
 * 1戦でのトロフィー(ランク戦ではレーティング)の増減。公式APIの trophyChange を、意味の分かっている対戦だけで使う。
 * 通常のトロフィー戦(type が PvP)はトロフィー。ランク戦(pathOfLegend)は天界の対戦だけレーティング(リーグが分からなければレーティングとする)。
 * 天界より下のランク戦は、勝った側に常に+30が入り負けた側には値が無い。レーティングは天界だけのもので(ゲームデータの文言)、
 * 天界より下は「段」で進むので、+30が何の値か裏付けが取れるまで出さない(2026-10-14、約800戦で確認)。
 * イベント戦などにも trophyChange が入ることがある(+1 など)が、トロフィーではないので使わない(2026-10-04に実データで確認)。
 * ランク戦でも trophyChange が無い対戦があり、そのときは増減を出さない。
 */
public record TrophyChange(Kind kind, int amount) {

    public enum Kind {
        TROPHIES, RATING
    }

    /** @param leagueNumber その対戦のリーグ(公式APIの対戦履歴の leagueNumber) */
    public static Optional<TrophyChange> of(String battleType, Integer trophyChange, Integer leagueNumber) {
        if (trophyChange == null || trophyChange == 0) {
            return Optional.empty();
        }
        Kind kind = switch (battleType == null ? "" : battleType) {
            case "PvP" -> Kind.TROPHIES;
            case "pathOfLegend" -> RankedLeague.of(leagueNumber).orElse(RankedLeague.TOP) == RankedLeague.TOP
                    ? Kind.RATING : null;
            default -> null;
        };
        return kind == null ? Optional.empty() : Optional.of(new TrophyChange(kind, trophyChange));
    }

    public boolean gained() {
        return amount > 0;
    }
}
