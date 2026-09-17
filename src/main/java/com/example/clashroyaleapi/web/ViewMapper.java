package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.ClanRankingResponse;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.ClanSearchResponse;
import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;
import com.example.clashroyaleapi.domain.BattleResult;
import com.example.clashroyaleapi.domain.Country;
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

import java.text.Collator;
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

    public ViewMapper(LabelResolver labels) {
        this.labels = labels;
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
                    return new ClanMemberView(Tags.toPathSegment(member.tag()), DisplayNames.of(member.name()),
                            labels.role(member.role(), locale), member.trophies(), member.donations(),
                            days.isPresent() ? days.getAsLong() : null, MemberActivity.isLongInactive(days));
                })
                .toList();
    }

    public List<ClanRankingRowView> toClanRankingRows(List<ClanRankingResponse.RankedClan> clans, Locale locale) {
        return clans.stream()
                .map(clan -> new ClanRankingRowView(clan.rank(), Tags.toPathSegment(clan.tag()), clan.tag(),
                        DisplayNames.of(clan.name()), clan.clanScore(), clan.members(),
                        locationName(clan.location(), locale)))
                .toList();
    }

    private String locationName(ClanRankingResponse.Location location, Locale locale) {
        if (location == null) {
            return null;
        }
        if (location.countryCode() == null || location.countryCode().isBlank()) {
            return location.name();
        }
        return countryName(new Country(null, location.countryCode(), location.name()), locale);
    }

    /**
     * 画面が対応しているのは日本語と英語だけなので、国名もそのどちらかに寄せる。
     * 解決されたロケール(Accept-Language由来でde等になり得る)をそのまま使うと、
     * 英語表示の画面に "Deutschland" のような現地語の国名が混ざる。
     */
    private static Locale displayLocale(Locale locale) {
        return "ja".equals(locale.getLanguage()) ? Locale.JAPANESE : Locale.ENGLISH;
    }

    public List<PlayerRankingRowView> toPlayerRankingRows(List<PlayerRankingResponse.RankedPlayer> players) {
        return players.stream()
                .map(player -> new PlayerRankingRowView(player.rank(), Tags.toPathSegment(player.tag()), player.tag(),
                        DisplayNames.of(player.name()), player.expLevel(), player.eloRating(),
                        player.clan() == null ? null : DisplayNames.of(player.clan().name()),
                        player.clan() == null ? null : Tags.toPathSegment(player.clan().tag())))
                .toList();
    }

    public List<CountryOptionView> toCountryOptions(List<Country> countries, Locale locale) {
        Collator collator = Collator.getInstance(locale);
        return countries.stream()
                .map(country -> new CountryOptionView(country.countryCode(), countryName(country, locale)))
                .sorted((left, right) -> collator.compare(left.name(), right.name()))
                .toList();
    }

    /**
     * 国名の訳はJDK(CLDR)の翻訳をそのまま使う。200件超の訳語を自前で持つとメンテできないため。
     * 公式APIが返す国コードにはISO以外のものも含まれ、その場合はJDKが訳せずコードをそのまま返すので、
     * 公式APIの英語名にフォールバックする。
     */
    public String countryName(Country country, Locale locale) {
        String displayName = Locale.of("", country.countryCode()).getDisplayCountry(displayLocale(locale));
        return displayName.isBlank() || displayName.equals(country.countryCode())
                ? country.englishName()
                : displayName;
    }

    public List<ClanSummaryView> toClanSummaries(List<ClanSearchResponse.ClanSummary> clans) {
        return clans.stream()
                .map(clan -> new ClanSummaryView(Tags.toPathSegment(clan.tag()), clan.tag(),
                        DisplayNames.of(clan.name()), clan.clanScore(), clan.members()))
                .toList();
    }

    private BattleSummaryView toBattleSummary(BattleLogEntry battle, String viewerTag, Locale locale) {
        return new BattleSummaryView(
                battle.battleTime(),
                gameModeOf(battle, locale),
                resultOf(battle),
                crownsOf(battle.team()),
                crownsOf(battle.opponent()),
                toLinks(battle.opponent()),
                toLinks(battle.team().stream().filter(p -> !isViewer(p, viewerTag)).toList()));
    }

    private static List<PlayerLinkView> toLinks(List<BattleLogEntry.Participant> participants) {
        return participants.stream()
                .map(p -> new PlayerLinkView(DisplayNames.of(p.name()), Tags.toPathSegment(p.tag())))
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
                        DisplayNames.of(participant.name()),
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
        return labels.gameMode(battle.gameMode() != null ? battle.gameMode().name() : null, locale);
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
