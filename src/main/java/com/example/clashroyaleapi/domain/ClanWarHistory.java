package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.ApiTimestamp;
import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.client.dto.RiverRaceLogResponse;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/** クラン情報画面の「クラン対戦成績」。直近の週ごとの、このクランの順位・メダル・クラントロフィーの増減(新しい順)。 */
public record ClanWarHistory(List<Week> weeks) {

    /**
     * rank が null の週は、メダルが0だった週。公式APIは0のクランどうしにも2位〜5位を振るが、
     * 同点の中の並びに意味は無い(全員のトロフィーの増減が同じ)ため、順位として出さない。
     */
    public record Week(LocalDate endedOn, Integer rank, int medals, int trophyChange) {

        public boolean isFirstPlace() {
            return rank != null && rank == 1;
        }
    }

    public static ClanWarHistory of(String clanTag, RiverRaceLogResponse log) {
        if (log == null || log.items() == null) {
            return new ClanWarHistory(List.of());
        }
        String tag = Tags.normalize(clanTag);
        List<Week> weeks = log.items().stream()
                .map(race -> toWeek(tag, race))
                .filter(Objects::nonNull)
                .toList();
        return new ClanWarHistory(weeks);
    }

    private static Week toWeek(String clanTag, RiverRaceLogResponse.RiverRace race) {
        if (race.standings() == null) {
            return null;
        }
        LocalDate endedOn = ApiTimestamp.parse(race.createdDate())
                .map(at -> LocalDate.ofInstant(at, ZoneOffset.UTC))
                .orElse(null);
        if (endedOn == null) {
            return null;
        }
        return race.standings().stream()
                .filter(s -> s.clan() != null && s.clan().tag() != null
                        && Tags.normalize(s.clan().tag()).equals(clanTag))
                .findFirst()
                .map(s -> new Week(endedOn, s.clan().fame() > 0 ? s.rank() : null, s.clan().fame(),
                        s.trophyChange()))
                .orElse(null);
    }

    public int firstPlaces() {
        return (int) weeks.stream().filter(Week::isFirstPlace).count();
    }

    /** 一番新しい週から続けて1位だった週の数。 */
    public int firstPlaceStreak() {
        int streak = 0;
        for (Week week : weeks) {
            if (!week.isFirstPlace()) {
                break;
            }
            streak++;
        }
        return streak;
    }
}
