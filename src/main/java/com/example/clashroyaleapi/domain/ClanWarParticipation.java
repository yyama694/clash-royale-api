package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.CurrentRiverRaceResponse;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 今のメンバーそれぞれの、今週のクラン対戦での使用デッキ数。
 * 対戦の参加者には元メンバーが含まれ、逆に加入したばかりで参加者にまだいないメンバーもいるため、
 * メンバー一覧を基準に突き合わせる(参加者にいない人は0として扱う)。
 */
public record ClanWarParticipation(boolean battleDay, List<Member> members) {

    public static final int DECKS_PER_DAY = 4;

    private static final Set<String> BATTLE_DAY_PERIODS = Set.of("warDay", "colosseum");

    public record Member(String tag, String name, int decksUsedToday, int decksUsedThisWeek) {
    }

    /** 今日まだ攻撃していない人を探しやすいよう、今日の使用数が少ない順(同数は今週の累計が少ない順)に並べる。 */
    public static ClanWarParticipation of(List<ClanResponse.Member> memberList, CurrentRiverRaceResponse race) {
        Map<String, CurrentRiverRaceResponse.Participant> participants =
                race.clan() == null || race.clan().participants() == null ? Map.of()
                        : race.clan().participants().stream()
                                .filter(p -> p.tag() != null)
                                .collect(Collectors.toMap(p -> Tags.normalize(p.tag()), Function.identity(),
                                        (first, second) -> first));
        List<Member> members = (memberList == null ? List.<ClanResponse.Member>of() : memberList).stream()
                .map(member -> {
                    CurrentRiverRaceResponse.Participant p = participants.get(Tags.normalize(member.tag()));
                    return new Member(member.tag(), member.name(),
                            p == null ? 0 : p.decksUsedToday(), p == null ? 0 : p.decksUsed());
                })
                .sorted(Comparator.comparingInt(Member::decksUsedToday).thenComparingInt(Member::decksUsedThisWeek))
                .toList();
        return new ClanWarParticipation(BATTLE_DAY_PERIODS.contains(race.periodType()), members);
    }

    public int membersBattledToday() {
        return (int) members.stream().filter(m -> m.decksUsedToday() > 0).count();
    }

    public int decksUsedToday() {
        return members.stream().mapToInt(Member::decksUsedToday).sum();
    }

    public int maxDecksToday() {
        return members.size() * DECKS_PER_DAY;
    }
}
