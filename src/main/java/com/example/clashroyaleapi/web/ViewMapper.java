package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.client.dto.ClanRankingResponse;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.ClanSearchResponse;
import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.domain.BattleExclusion;
import com.example.clashroyaleapi.domain.BattleResult;
import com.example.clashroyaleapi.domain.CardCollection;
import com.example.clashroyaleapi.domain.CardForm;
import com.example.clashroyaleapi.domain.CardLevel;
import com.example.clashroyaleapi.domain.CardUsage;
import com.example.clashroyaleapi.domain.ClanType;
import com.example.clashroyaleapi.domain.ClanWarParticipation;
import com.example.clashroyaleapi.domain.Country;
import com.example.clashroyaleapi.domain.CurrentDeck;
import com.example.clashroyaleapi.domain.Deck;
import com.example.clashroyaleapi.domain.FavoriteFetch;
import com.example.clashroyaleapi.domain.GameText;
import com.example.clashroyaleapi.domain.MemberActivity;
import com.example.clashroyaleapi.domain.PlayerBattleStats;
import com.example.clashroyaleapi.domain.PlayerNameMatch;
import com.example.clashroyaleapi.domain.TopDecks;
import com.example.clashroyaleapi.domain.TrophyChange;
import com.example.clashroyaleapi.domain.WinLoseStreak;
import com.example.clashroyaleapi.service.CardService;
import com.example.clashroyaleapi.service.PlayerRanking;
import com.example.clashroyaleapi.web.view.BattleDetailView;
import com.example.clashroyaleapi.web.view.BattleStatsView;
import com.example.clashroyaleapi.web.view.BattleSummaryView;
import com.example.clashroyaleapi.web.view.CardCatalogGroupView;
import com.example.clashroyaleapi.web.view.CardCatalogItemView;
import com.example.clashroyaleapi.web.view.CardCollectionView;
import com.example.clashroyaleapi.web.view.CardDetailView;
import com.example.clashroyaleapi.web.view.CardOptionView;
import com.example.clashroyaleapi.web.view.CardPerformanceView;
import com.example.clashroyaleapi.web.view.CardUsageView;
import com.example.clashroyaleapi.web.view.CardView;
import com.example.clashroyaleapi.web.view.ClanJoinView;
import com.example.clashroyaleapi.web.view.ClanMemberView;
import com.example.clashroyaleapi.web.view.ClanRankingRowView;
import com.example.clashroyaleapi.web.view.ClanSummaryView;
import com.example.clashroyaleapi.web.view.ClanWarView;
import com.example.clashroyaleapi.web.view.CountryOptionView;
import com.example.clashroyaleapi.web.view.CurrentDeckView;
import com.example.clashroyaleapi.web.view.DeckMetaView;
import com.example.clashroyaleapi.web.view.ElixirBadgeView;
import com.example.clashroyaleapi.web.view.FavoriteClanView;
import com.example.clashroyaleapi.web.view.FavoritePlayerView;
import com.example.clashroyaleapi.web.view.FavoriteRowStatus;
import com.example.clashroyaleapi.web.view.OpponentView;
import com.example.clashroyaleapi.web.view.ParticipantView;
import com.example.clashroyaleapi.web.view.PlayerLinkView;
import com.example.clashroyaleapi.web.view.PlayerNameMatchView;
import com.example.clashroyaleapi.web.view.PlayerProgressView;
import com.example.clashroyaleapi.web.view.PlayerRankingRowView;
import com.example.clashroyaleapi.web.view.PlayerRankingView;
import com.example.clashroyaleapi.web.view.TopPlayerDeckView;
import com.example.clashroyaleapi.web.view.TrophyChangeView;

import com.ibm.icu.text.Collator;
import com.ibm.icu.util.ULocale;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
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
                .filter(BattleResult::hasBothSides)
                .map(battle -> toBattleSummary(battle, viewerTag, locale))
                .toList();
    }

    /** battle は両側がそろったもの(PlayerService#findBattle が片側の欠けた対戦を返さない)。 */
    public BattleDetailView toBattleDetail(BattleLogEntry battle, String viewerTag, Locale locale) {
        BattleResult teamResult = BattleResult.of(battle);
        return new BattleDetailView(
                battle.battleTime(),
                timeFormatter.apiTimestamp(battle.battleTime(), locale),
                gameModeOf(battle, locale),
                toParticipants(viewerFirst(battle.team(), viewerTag), teamResult, battle.type(), viewerTag, locale),
                toParticipants(battle.opponent(), teamResult.opposite(), battle.type(), viewerTag, locale));
    }

    /** デッキが空(公式APIが返さなかった)の場合は、画面に案内文を出すため空を返す。 */
    public CurrentDeckView toCurrentDeck(CurrentDeck deck, Locale locale) {
        return new CurrentDeckView(
                toCards(deck.cards(), false, locale),
                toCards(deck.supportCards(), true, locale),
                toDeckMeta(deck.cards(), deck.supportCards()),
                deck.completedFromBattle());
    }

    /**
     * カード詳細。公式APIが返さない項目(説明文・ステータス)は無いため、ここにある内容が全て。
     * 進化画像は、進化が実装されているカードにだけ付く(maxEvolutionLevelがあっても画像が無いカードがある)。
     */
    public CardDetailView toCardDetail(CardsResponse.Card card, Locale locale) {
        CardsResponse.Card.IconUrls icons = card.iconUrls();
        return new CardDetailView(
                card.id(),
                labels.cardName(card.name(), locale),
                card.name(),
                icons == null ? null : icons.medium(),
                icons == null ? null : icons.evolutionMedium(),
                icons == null ? null : icons.heroMedium(),
                labels.rarity(card.rarity(), locale),
                card.elixirCost(),
                CardLevel.inGame(1, card.maxLevel()),
                CardLevel.inGame(card.maxLevel(), card.maxLevel()));
    }

    /**
     * 一緒に使われるカードの名前・画像はカード一覧から引く。一覧に無いID(集計後に削除されたカードなど)は出さない。
     */
    public CardUsageView toCardUsage(CardUsage usage, int cardId, Map<Integer, CardsResponse.Card> cardsById,
            Locale locale) {
        CardUsage.Usage card = usage.usageOf(cardId);
        List<CardUsageView.PartnerView> partners = card.partners().stream()
                .filter(partner -> cardsById.containsKey(partner.cardId()))
                .map(partner -> {
                    CardsResponse.Card c = cardsById.get(partner.cardId());
                    return new CardUsageView.PartnerView(c.id(), labels.cardName(c.name(), locale),
                            c.iconUrls() == null ? null : c.iconUrls().medium(), partner.percent());
                })
                .toList();
        return new CardUsageView(
                Math.round(card.percent() * 10) / 10.0,
                card.users(),
                card.sampleSize(),
                card.rank() == 0 ? null : card.rank(),
                card.rankedOf(),
                timeFormatter.instant(usage.collectedAt(), locale),
                partners);
    }

    /**
     * 集計ファイルにはカードIDとレベルしか無いので、名前・画像・エリクサーはカード一覧から引く。
     * 一覧に無いID(集計後に削除されたカードなど)は、名前の代わりにIDを出す。
     */
    public TopPlayerDeckView toTopPlayerDeck(TopDecks.SampledDeck deck, Map<Integer, CardsResponse.Card> cardsById,
            Locale locale) {
        TopDecks.Player player = deck.player();
        List<CardView> cards = new ArrayList<>();
        List<Integer> elixirCosts = new ArrayList<>();
        for (int i = 0; i < deck.cardIds().size(); i++) {
            int id = deck.cardIds().get(i);
            int level = i < player.levels().size() ? player.levels().get(i) : 0;
            CardsResponse.Card card = cardsById.get(id);
            cards.add(toCardView(id, card, level, player.formAt(i), false, locale));
            elixirCosts.add(card == null ? null : card.elixirCost());
        }
        List<CardView> support = deck.towerTroopId() == null ? List.of()
                : List.of(toCardView(deck.towerTroopId(), cardsById.get(deck.towerTroopId()),
                        player.towerLevel() == null ? 0 : player.towerLevel(), CardForm.NORMAL, true, locale));
        OptionalInt cycle = Deck.fourCardCycle(elixirCosts);
        DeckMetaView meta = new DeckMetaView(
                toNullable(Deck.averageElixir(elixirCosts)),
                cycle.isPresent() ? cycle.getAsInt() : null,
                toNullable(Deck.averageLevel(player.levels())),
                Deck.copyUrl(deck.cardIds(), deck.towerTroopId()).orElse(null));
        return new TopPlayerDeckView(player.rank(), player.name(), Tags.toPathSegment(player.tag()), player.rating(),
                cards, support, meta);
    }

    private CardView toCardView(int id, CardsResponse.Card card, int level, CardForm form, boolean tower,
            Locale locale) {
        if (card == null) {
            return new CardView(id, String.valueOf(id), null, level, CardForm.NORMAL, null, null);
        }
        CardsResponse.Card.IconUrls icons = card.iconUrls();
        return new CardView(id, labels.cardName(card.name(), locale),
                icons == null ? null : iconFor(form, icons.medium(), icons.evolutionMedium(), icons.heroMedium()),
                level, form, formLabel(form, locale), toElixirBadge(card.elixirCost(), tower, locale));
    }

    /** 進化・ヒーローの画像が無いカードは通常の画像にする。 */
    private static String iconFor(CardForm form, String medium, String evolution, String hero) {
        String url = switch (form) {
            case EVOLUTION -> evolution;
            case HERO -> hero;
            case NORMAL -> null;
        };
        return url != null ? url : medium;
    }

    private String formLabel(CardForm form, Locale locale) {
        return switch (form) {
            case EVOLUTION -> labels.message("card.evolution", locale);
            case HERO -> labels.message("card.hero", locale);
            case NORMAL -> null;
        };
    }

    /** カードで絞り込むときの選択肢。表示言語の名前の順に並べる(漢字で始まる日本語名は読み仮名で。国名と同じ)。 */
    public List<CardOptionView> toCardOptions(Collection<CardsResponse.Card> cards, Locale locale) {
        Collator collator = Collator.getInstance(ULocale.forLocale(SupportedLanguages.displayLocale(locale)));
        return cards.stream()
                .sorted(Comparator.comparing((CardsResponse.Card card) -> labels.cardSortKey(card.name(), locale),
                        collator))
                .map(card -> new CardOptionView(card.id(), labels.cardName(card.name(), locale)))
                .toList();
    }

    public String cardName(CardsResponse.Card card, Locale locale) {
        return labels.cardName(card.name(), locale);
    }

    /**
     * 画面に出せるキングタワーレベル。導入(2026-05-26のアップデート)以降ログインしていないアカウントは公式APIが0を返すが、
     * ゲーム内の最小は1なので、そのときは null にして「-」と出す(2026-09-30、育成途中の24人中9人)。
     */
    public Integer kingTowerLevel(PlayerResponse player) {
        return player.kingTowerLevel() > 0 ? player.kingTowerLevel() : null;
    }

    public PlayerProgressView toPlayerProgress(PlayerResponse player) {
        PlayerResponse.RankedSeasonResult ranked = player.currentPathOfLegendSeasonResult();
        return new PlayerProgressView(player.trophies(), player.wins(), player.losses(),
                ranked != null && ranked.hasRank() ? ranked.rank() : null, rankedRating(player));
    }

    /** ランク戦の順位が無い人のレーティングは0などの意味の無い値なので、順位があるときだけ返す。 */
    private static Integer rankedRating(PlayerResponse player) {
        PlayerResponse.RankedSeasonResult ranked = player.currentPathOfLegendSeasonResult();
        return ranked != null && ranked.hasRank() ? ranked.trophies() : null;
    }

    public String toStreakLabel(WinLoseStreak streak, Locale locale) {
        return labels.message("player.streak." + streak.code(), locale, streak.count());
    }

    public CardCollectionView toCardCollection(CardCollection collection, Locale locale) {
        return new CardCollectionView(
                collection.totalCards(),
                collection.maxedCards(),
                percentOf(collection.maxedCards(), collection.totalCards()),
                collection.rarities().stream()
                        .map(r -> new CardCollectionView.RaritySummaryView(
                                labels.rarity(r.rarity(), locale),
                                r.totalCards(),
                                r.maxedCards(),
                                percentOf(r.maxedCards(), r.totalCards())))
                        .toList());
    }

    private static int percentOf(int part, int total) {
        return total == 0 ? 0 : Math.round(part * 100f / total);
    }

    public List<CardCatalogGroupView> toCardCatalog(List<CardService.CardGroup> groups, Locale locale) {
        return groups.stream()
                .map(group -> {
                    boolean tower = group.rarity() == null;
                    return new CardCatalogGroupView(
                            tower ? labels.message("cards.group.tower", locale) : labels.rarity(group.rarity(), locale),
                            group.cards().stream()
                                    .map(card -> {
                                        String name = labels.cardName(card.name(), locale);
                                        return new CardCatalogItemView(card.id(), name,
                                                searchText(name, card.name(), labels.cardAlias(card.name(), locale)),
                                                card.iconUrls() == null ? null : card.iconUrls().medium(),
                                                toElixirBadge(card.elixirCost(), tower, locale), card.elixirCost());
                                    })
                                    .toList());
                })
                .toList();
    }

    private static String searchText(String name, String englishName, String alias) {
        return alias == null ? name + " " + englishName : name + " " + englishName + " " + alias;
    }

    /**
     * タワーユニットはエリクサーを払わないのでバッジを出さない。
     * 鏡のようにコストが固定でないカードは、ゲーム内と同じく "?" を出す(何も出さないと表示漏れに見えるため)。
     */
    private ElixirBadgeView toElixirBadge(Integer cost, boolean tower, Locale locale) {
        if (tower) {
            return null;
        }
        if (cost == null) {
            return new ElixirBadgeView("?", labels.message("cards.elixir.variable", locale));
        }
        return new ElixirBadgeView(String.valueOf(cost), labels.message("cards.elixir", locale, cost));
    }

    public BattleStatsView toStats(PlayerBattleStats stats, Locale locale) {
        return new BattleStatsView(stats.total(), stats.wins(), stats.losses(), stats.draws(), stats.friendlyExcluded(),
                stats.boatDefenseExcluded(), stats.cardRanking(), toPerformances(stats.favoriteCards(), locale),
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

    public ClanJoinView toClanJoin(ClanResponse clan, Locale locale) {
        Optional<ClanType> type = ClanType.from(clan.type());
        return new ClanJoinView(type.map(t -> labels.message(t.messageKey(), locale)).orElse(null),
                clan.requiredTrophies(), locationName(clan.location(), locale), clan.donationsPerWeek(),
                type.map(t -> t.openSlots(clan.members())).orElse(0));
    }

    public ClanWarView toClanWar(ClanWarParticipation war) {
        return new ClanWarView(
                war.battleDay(),
                war.membersBattledToday(),
                war.members().size(),
                war.decksUsedToday(),
                war.maxDecksToday(),
                ClanWarParticipation.DECKS_PER_DAY,
                war.members().stream()
                        .map(m -> new ClanWarView.MemberView(Tags.toPathSegment(m.tag()),
                                GameText.stripFormatting(m.name()), m.decksUsedToday(), m.decksUsedThisWeek(),
                                m.decksUsedToday() == 0))
                        .toList());
    }

    public List<ClanRankingRowView> toClanRankingRows(List<ClanRankingResponse.RankedClan> clans,
            Map<String, Integer> warTrophiesByTag, Locale locale) {
        return clans.stream()
                .map(clan -> new ClanRankingRowView(clan.rank(), Tags.toPathSegment(clan.tag()), clan.tag(),
                        GameText.stripFormatting(clan.name()), clan.clanScore(), clan.members(),
                        locationName(clan.location(), locale), warTrophiesByTag.get(clan.tag())))
                .toList();
    }

    private String locationName(ClanRankingResponse.Location location, Locale locale) {
        return location == null ? null : countryNames.locationName(location.countryCode(), location.name(), locale);
    }

    public PlayerRankingView toPlayerRanking(PlayerRanking ranking, Locale locale) {
        String notice = switch (ranking.status()) {
            case CURRENT_SEASON -> null;
            case FINISHED_SEASON -> labels.message("playerRanking.finishedSeason", locale,
                    seasonMonth(ranking.finishedSeason()));
            case EMPTY -> labels.message("playerRanking.empty", locale);
            case UNAVAILABLE -> labels.message("ranking.unavailable", locale);
        };
        return new PlayerRankingView(toPlayerRankingRows(ranking.players()), notice);
    }

    // 月名は言語ごとにICUに書かせる(引数は日時)。サーバーのタイムゾーンで前後の月にずれないよう、月の半ばにする。
    private static Date seasonMonth(YearMonth season) {
        return Date.from(season.atDay(15).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    private List<PlayerRankingRowView> toPlayerRankingRows(List<PlayerRankingResponse.RankedPlayer> players) {
        return players.stream()
                .map(player -> new PlayerRankingRowView(player.rank(), Tags.toPathSegment(player.tag()), player.tag(),
                        GameText.stripFormatting(player.name()), player.eloRating(),
                        player.clan() == null ? null : GameText.stripFormatting(player.clan().name()),
                        player.clan() == null ? null : Tags.toPathSegment(player.clan().tag())))
                .toList();
    }

    public List<FavoritePlayerView> toFavoritePlayerRows(List<FavoriteFetch<PlayerResponse>> results) {
        return results.stream().map(this::toFavoritePlayerRow).toList();
    }

    private FavoritePlayerView toFavoritePlayerRow(FavoriteFetch<PlayerResponse> result) {
        String pathTag = result.tag();
        String tag = Tags.normalize(pathTag);
        if (result instanceof FavoriteFetch.Found<PlayerResponse> found) {
            PlayerResponse player = found.value();
            return new FavoritePlayerView(FavoriteRowStatus.FOUND, pathTag, tag,
                    GameText.stripFormatting(player.name()), player.trophies(), rankedRating(player),
                    player.clan() == null ? null : GameText.stripFormatting(player.clan().name()),
                    player.clan() == null ? null : Tags.toPathSegment(player.clan().tag()));
        }
        FavoriteRowStatus status = result instanceof FavoriteFetch.NotFound<PlayerResponse>
                ? FavoriteRowStatus.NOT_FOUND : FavoriteRowStatus.UNAVAILABLE;
        return new FavoritePlayerView(status, pathTag, tag, result.name(), null, null, null, null);
    }

    public List<FavoriteClanView> toFavoriteClanRows(List<FavoriteFetch<ClanResponse>> results) {
        return results.stream().map(this::toFavoriteClanRow).toList();
    }

    private FavoriteClanView toFavoriteClanRow(FavoriteFetch<ClanResponse> result) {
        String pathTag = result.tag();
        String tag = Tags.normalize(pathTag);
        if (result instanceof FavoriteFetch.Found<ClanResponse> found) {
            ClanResponse clan = found.value();
            return new FavoriteClanView(FavoriteRowStatus.FOUND, pathTag, tag,
                    GameText.stripFormatting(clan.name()), clan.clanScore(), clan.members());
        }
        FavoriteRowStatus status = result instanceof FavoriteFetch.NotFound<ClanResponse>
                ? FavoriteRowStatus.NOT_FOUND : FavoriteRowStatus.UNAVAILABLE;
        return new FavoriteClanView(status, pathTag, tag, result.name(), null, null);
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

    public List<PlayerNameMatchView> toPlayerNameMatches(List<PlayerNameMatch> players, Locale locale) {
        return players.stream()
                .map(player -> new PlayerNameMatchView(GameText.stripFormatting(player.name()), player.tag(),
                        Tags.toPathSegment(player.tag()),
                        player.lastSeen() == null ? null : timeFormatter.instant(player.lastSeen(), locale)))
                .toList();
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
                BattleResult.of(battle),
                BattleResult.crownsOf(battle.team()),
                BattleResult.crownsOf(battle.opponent()),
                toOpponents(battle.opponent()),
                toLinks(battle.team().stream().filter(p -> !isViewer(p, viewerTag)).toList()),
                battle.team().stream().filter(p -> isViewer(p, viewerTag)).findFirst()
                        .map(viewer -> toTrophyChange(battle.type(), viewer, locale)).orElse(null));
    }

    private TrophyChangeView toTrophyChange(String battleType, BattleLogEntry.Participant participant, Locale locale) {
        return TrophyChange.of(battleType, participant.trophyChange())
                .map(change -> new TrophyChangeView(
                        labels.message(change.kind() == TrophyChange.Kind.TROPHIES
                                ? "battlelog.change.trophies" : "battlelog.change.rating", locale),
                        (change.gained() ? "+" : "") + NumberFormat.getIntegerInstance(locale).format(change.amount()),
                        change.gained()))
                .orElse(null);
    }

    private static List<PlayerLinkView> toLinks(List<BattleLogEntry.Participant> participants) {
        return participants.stream()
                .map(p -> new PlayerLinkView(GameText.stripFormatting(p.name()), Tags.toPathSegment(p.tag())))
                .toList();
    }

    /** 2v2はカードレベルがほぼ揃えられていて比べる意味が薄いため、平均レベルを出さない。 */
    private static List<OpponentView> toOpponents(List<BattleLogEntry.Participant> participants) {
        boolean teamBattle = participants.size() > 1;
        return participants.stream()
                .map(p -> new OpponentView(GameText.stripFormatting(p.name()), Tags.toPathSegment(p.tag()),
                        teamBattle ? null : toNullable(Deck.averageLevel(inGameLevels(p.cards())))))
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
            String battleType, String viewerTag, Locale locale) {
        return side.stream()
                .map(participant -> new ParticipantView(
                        participant.tag(),
                        Tags.toPathSegment(participant.tag()),
                        GameText.stripFormatting(participant.name()),
                        participant.crowns(),
                        result,
                        toCards(participant.cards(), false, locale),
                        toCards(participant.supportCards(), true, locale),
                        toDeckMeta(participant.cards(), participant.supportCards()),
                        isViewer(participant, viewerTag),
                        toTrophyChange(battleType, participant, locale)))
                .toList();
    }

    static DeckMetaView toDeckMeta(List<BattleLogEntry.Card> deck, List<BattleLogEntry.Card> supportCards) {
        List<BattleLogEntry.Card> cards = deck == null ? List.of() : deck;
        List<Integer> elixirCosts = cards.stream().map(BattleLogEntry.Card::elixirCost).toList();
        List<Integer> cardIds = cards.stream().map(BattleLogEntry.Card::id).toList();
        Integer towerTroopId = supportCards == null || supportCards.isEmpty() ? null : supportCards.get(0).id();
        OptionalInt cycle = Deck.fourCardCycle(elixirCosts);
        return new DeckMetaView(
                toNullable(Deck.averageElixir(elixirCosts)),
                cycle.isPresent() ? cycle.getAsInt() : null,
                toNullable(Deck.averageLevel(inGameLevels(cards))),
                Deck.copyUrl(cardIds, towerTroopId).orElse(null));
    }

    private static List<Integer> inGameLevels(List<BattleLogEntry.Card> cards) {
        if (cards == null) {
            return List.of();
        }
        return cards.stream().map(card -> CardLevel.inGame(card.level(), card.maxLevel())).toList();
    }

    private static Double toNullable(OptionalDouble value) {
        return value.isPresent() ? value.getAsDouble() : null;
    }

    /** tower はタワーユニットの並びか(エリクサーを払わず、進化・ヒーローも無い)。 */
    private List<CardView> toCards(List<BattleLogEntry.Card> cards, boolean tower, Locale locale) {
        if (cards == null) {
            return List.of();
        }
        return cards.stream()
                .map(card -> {
                    CardForm form = tower ? CardForm.NORMAL : CardForm.ofBattle(card.evolutionLevel());
                    BattleLogEntry.IconUrls icons = card.iconUrls();
                    return new CardView(card.id(), labels.cardName(card.name(), locale),
                            icons == null ? null
                                    : iconFor(form, icons.medium(), icons.evolutionMedium(), icons.heroMedium()),
                            CardLevel.inGame(card.level(), card.maxLevel()), form, formLabel(form, locale),
                            toElixirBadge(card.elixirCost(), tower, locale));
                })
                .toList();
    }

    private List<CardPerformanceView> toPerformances(List<PlayerBattleStats.CardPerformance> performances,
            Locale locale) {
        return performances.stream()
                .map(card -> new CardPerformanceView(card.cardId(), labels.cardName(card.cardName(), locale),
                        card.iconUrl(), card.uses(), card.wins(), card.winRatePercent()))
                .toList();
    }

    private String gameModeOf(BattleLogEntry battle, Locale locale) {
        // 戦績サマリーと連勝の集計から外している対戦なので、一覧でも見分けられるようにする。
        if (BattleExclusion.of(battle).orElse(null) == BattleExclusion.BOAT_DEFENSE) {
            return labels.message("battletype.boatDefense", locale);
        }
        return labels.gameMode(battle.type(), battle.gameMode() != null ? battle.gameMode().name() : null, locale);
    }
}
