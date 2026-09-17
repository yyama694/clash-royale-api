package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.ClanRankingResponse;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.ClanSearchResponse;
import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;
import com.example.clashroyaleapi.domain.BattleResult;
import com.example.clashroyaleapi.domain.Country;
import com.example.clashroyaleapi.domain.GameText;
import com.example.clashroyaleapi.domain.MemberActivity;
import com.example.clashroyaleapi.domain.PlayerBattleStats;
import com.example.clashroyaleapi.web.view.BattleDetailView;
import com.example.clashroyaleapi.web.view.BattleStatsView;
import com.example.clashroyaleapi.web.view.BattleSummaryView;
import com.example.clashroyaleapi.web.view.CardPerformanceView;
import com.example.clashroyaleapi.web.view.CardView;
import com.example.clashroyaleapi.web.view.ClanMemberView;
import com.example.clashroyaleapi.web.view.ClanRankingRowView;
import com.example.clashroyaleapi.web.view.ClanSummaryView;
import com.example.clashroyaleapi.web.view.CountryOptionView;
import com.example.clashroyaleapi.web.view.ParticipantView;
import com.example.clashroyaleapi.web.view.PlayerLinkView;
import com.example.clashroyaleapi.web.view.PlayerRankingRowView;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * APIのDTOを、表示用に解決済みのViewModelへ変換する。
 * テンプレート側からロジック(勝敗判定・ラベル解決)を追い出すのが目的。
 */
@Component
public class ViewMapper {

    private final LabelResolver labels;
    private final CountryNames countryNames;
    private final TimeFormatter timeFormatter;

    public ViewMapper(LabelResolver labels, CountryNames countryNames, TimeFormatter timeFormatter) {
        this.labels = labels;
        this.countryNames = countryNames;
        this.timeFormatter = timeFormatter;
    }

    /** viewerTag は対戦履歴を見ているプレイヤーのタグ。2v2で味方と区別するために使う。 */
    public List<BattleSummaryView> toBattleSummaries(List<BattleLogEntry> battleLog, String viewerTag,
            Locale locale) {
        return battleLog.stream()
                .filter(ViewMapper::hasBothSides)
                .map(battle -> toBattleSummary(battle, viewerTag, locale))
                .toList();
    }

    public BattleDetailView toBattleDetail(BattleLogEntry battle, String viewerTag, Locale locale) {
        BattleResult teamResult = resultOf(battle);
        return new BattleDetailView(
                battle.battleTime(),
                timeFormatter.apiTimestamp(battle.battleTime(), locale),
                gameModeOf(battle, locale),
                toParticipants(viewerFirst(battle.team(), viewerTag), teamResult, viewerTag, locale),
                toParticipants(battle.opponent(), invert(teamResult), viewerTag, locale));
    }

    public BattleStatsView toStats(PlayerBattleStats stats, Locale locale) {
        return new BattleStatsView(stats.total(), stats.wins(), stats.losses(), stats.draws(),
                toPerformances(stats.favoriteCards(), locale),
                toPerformances(stats.weakCards(), locale));
    }

    public List<ClanMemberView> toMembers(List<ClanResponse.Member> members, Locale locale) {
        // 一覧内で基準時刻がずれないよう、1回だけ現在時刻を取る。
        Instant now = Instant.now();
        return members.stream()
                .map(member -> {
                    OptionalLong days = MemberActivity.inactiveDays(member.lastSeen(), now);
                    return new ClanMemberView(Tags.toPathSegment(member.tag()), GameText.stripFormatting(member.name()),
                            labels.role(member.role(), locale), member.trophies(), member.donations(),
                            days.isPresent() ? days.getAsLong() : null, MemberActivity.isLongInactive(days),
                            timeFormatter.apiTimestamp(member.lastSeen(), locale).iso());
                })
                .toList();
    }

    public List<ClanRankingRowView> toClanRankingRows(List<ClanRankingResponse.RankedClan> clans, Locale locale) {
        return clans.stream()
                .map(clan -> new ClanRankingRowView(clan.rank(), Tags.toPathSegment(clan.tag()), clan.tag(),
                        GameText.stripFormatting(clan.name()), clan.clanScore(), clan.members(),
                        locationName(clan.location(), locale)))
                .toList();
    }

    private String locationName(ClanRankingResponse.Location location, Locale locale) {
        return location == null ? null : countryNames.locationName(location.countryCode(), location.name(), locale);
    }

    public List<PlayerRankingRowView> toPlayerRankingRows(List<PlayerRankingResponse.RankedPlayer> players) {
        return players.stream()
                .map(player -> new PlayerRankingRowView(player.rank(), Tags.toPathSegment(player.tag()), player.tag(),
                        GameText.stripFormatting(player.name()), player.expLevel(), player.eloRating(),
                        player.clan() == null ? null : GameText.stripFormatting(player.clan().name()),
                        player.clan() == null ? null : Tags.toPathSegment(player.clan().tag())))
                .toList();
    }

    public List<CountryOptionView> toCountryOptions(List<Country> countries, Locale locale) {
        return countries.stream()
                .map(country -> new CountryOptionView(country.countryCode(), countryName(country, locale)))
                .sorted(countryNames.byDisplayOrder(CountryOptionView::code, CountryOptionView::name, locale))
                .toList();
    }

    public String countryName(Country country, Locale locale) {
        return countryNames.countryName(country.countryCode(), country.englishName(), locale);
    }

    public List<ClanSummaryView> toClanSummaries(List<ClanSearchResponse.ClanSummary> clans) {
        return clans.stream()
                .map(clan -> new ClanSummaryView(Tags.toPathSegment(clan.tag()), clan.tag(),
                        GameText.stripFormatting(clan.name()), clan.clanScore(), clan.members()))
                .toList();
    }

    private BattleSummaryView toBattleSummary(BattleLogEntry battle, String viewerTag, Locale locale) {
        return new BattleSummaryView(
                battle.battleTime(),
                timeFormatter.apiTimestamp(battle.battleTime(), locale),
                gameModeOf(battle, locale),
                resultOf(battle),
                crownsOf(battle.team()),
                crownsOf(battle.opponent()),
                toLinks(battle.opponent()),
                toLinks(battle.team().stream().filter(p -> !isViewer(p, viewerTag)).toList()));
    }

    private static List<PlayerLinkView> toLinks(List<BattleLogEntry.Participant> participants) {
        return participants.stream()
                .map(p -> new PlayerLinkView(GameText.stripFormatting(p.name()), Tags.toPathSegment(p.tag())))
                .toList();
    }

    /** 公式APIのteamは、見ているプレイヤーが先頭とは限らない(2v2で味方が先に来ることがある)。 */
    static List<BattleLogEntry.Participant> viewerFirst(List<BattleLogEntry.Participant> team, String viewerTag) {
        return team.stream()
                .sorted(Comparator.comparing(p -> !isViewer(p, viewerTag)))
                .toList();
    }

    private static boolean isViewer(BattleLogEntry.Participant participant, String viewerTag) {
        return viewerTag != null && participant.tag() != null
                && Tags.normalize(participant.tag()).equals(Tags.normalize(viewerTag));
    }

    private List<ParticipantView> toParticipants(List<BattleLogEntry.Participant> side, BattleResult result,
            String viewerTag, Locale locale) {
        return side.stream()
                .map(participant -> new ParticipantView(
                        participant.tag(),
                        Tags.toPathSegment(participant.tag()),
                        GameText.stripFormatting(participant.name()),
                        participant.crowns(),
                        result,
                        toCards(participant.cards(), locale),
                        toCards(participant.supportCards(), locale),
                        isViewer(participant, viewerTag)))
                .toList();
    }

    private List<CardView> toCards(List<BattleLogEntry.Card> cards, Locale locale) {
        if (cards == null) {
            return List.of();
        }
        return cards.stream()
                .map(card -> new CardView(labels.cardName(card.name(), locale), iconUrlOf(card), card.level()))
                .toList();
    }

    private List<CardPerformanceView> toPerformances(List<PlayerBattleStats.CardPerformance> performances,
            Locale locale) {
        return performances.stream()
                .map(card -> new CardPerformanceView(labels.cardName(card.cardName(), locale), card.iconUrl(),
                        card.uses(), card.wins(), card.winRatePercent()))
                .toList();
    }

    private String gameModeOf(BattleLogEntry battle, Locale locale) {
        return labels.gameMode(battle.type(), battle.gameMode() != null ? battle.gameMode().name() : null, locale);
    }

    private BattleResult resultOf(BattleLogEntry battle) {
        return BattleResult.of(crownsOf(battle.team()), crownsOf(battle.opponent()));
    }

    private static BattleResult invert(BattleResult result) {
        return switch (result) {
            case WIN -> BattleResult.LOSE;
            case LOSE -> BattleResult.WIN;
            case DRAW -> BattleResult.DRAW;
        };
    }

    private static boolean hasBothSides(BattleLogEntry battle) {
        return battle.team() != null && !battle.team().isEmpty()
                && battle.opponent() != null && !battle.opponent().isEmpty();
    }

    private static int crownsOf(List<BattleLogEntry.Participant> side) {
        return side.stream().mapToInt(BattleLogEntry.Participant::crowns).max().orElse(0);
    }

    private static String iconUrlOf(BattleLogEntry.Card card) {
        return card.iconUrls() != null ? card.iconUrls().medium() : null;
    }
}
