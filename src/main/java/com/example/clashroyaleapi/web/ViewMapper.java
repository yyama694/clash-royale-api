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
import com.example.clashroyaleapi.domain.PopularDeck;
import com.example.clashroyaleapi.domain.RankChange;
import com.example.clashroyaleapi.domain.RankedLeague;
import com.example.clashroyaleapi.domain.RankingMovements;
import com.example.clashroyaleapi.domain.RankingSnapshot;
import com.example.clashroyaleapi.domain.SeasonRankLog;
import com.example.clashroyaleapi.domain.SimilarDecks;
import com.example.clashroyaleapi.domain.TopDecks;
import com.example.clashroyaleapi.domain.TrophyChange;
import com.example.clashroyaleapi.domain.WinLoseStreak;
import com.example.clashroyaleapi.service.CardImageService;
import com.example.clashroyaleapi.service.CardService;
import com.example.clashroyaleapi.service.PlayerRanking;
import com.example.clashroyaleapi.service.RankingHistoryService;
import com.example.clashroyaleapi.service.RankingService;
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
import com.example.clashroyaleapi.web.view.DayLinkView;
import com.example.clashroyaleapi.web.view.DeckFilterRowView;
import com.example.clashroyaleapi.web.view.DeckMetaView;
import com.example.clashroyaleapi.web.view.ElixirBadgeView;
import com.example.clashroyaleapi.web.view.FavoriteClanView;
import com.example.clashroyaleapi.web.view.FavoritePlayerView;
import com.example.clashroyaleapi.web.view.FavoriteRowStatus;
import com.example.clashroyaleapi.web.view.OpponentView;
import com.example.clashroyaleapi.web.view.ParticipantView;
import com.example.clashroyaleapi.web.view.PlayerLinkView;
import com.example.clashroyaleapi.web.view.PlayerNameMatchView;
import com.example.clashroyaleapi.web.view.PlayerProfileView;
import com.example.clashroyaleapi.web.view.PlayerProgressView;
import com.example.clashroyaleapi.web.view.PlayerRankingRowView;
import com.example.clashroyaleapi.web.view.PlayerRankingView;
import com.example.clashroyaleapi.web.view.PopularDeckView;
import com.example.clashroyaleapi.web.view.RankChangeView;
import com.example.clashroyaleapi.web.view.RankedLeagueView;
import com.example.clashroyaleapi.web.view.RankHistoryView;
import com.example.clashroyaleapi.web.view.RankingReportView;
import com.example.clashroyaleapi.web.view.SeasonLinkView;
import com.example.clashroyaleapi.web.view.SimilarDecksView;
import com.example.clashroyaleapi.web.view.TopPlayerDeckView;
import com.example.clashroyaleapi.web.view.TrophyChangeView;

import com.ibm.icu.text.Collator;
import com.ibm.icu.text.DateFormat;
import com.ibm.icu.text.ListFormatter;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import java.util.Set;

/**
 * APIのDTOを、表示用に解決済みのViewModelへ変換する。
 * テンプレート側からロジック(勝敗判定・ラベル解決)を追い出すのが目的。
 */
@Component
public class ViewMapper {

    private final LabelResolver labels;
    private final CountryNames countryNames;
    private final TimeFormatter timeFormatter;
    private final CardImageService cardImages;

    public ViewMapper(LabelResolver labels, CountryNames countryNames, TimeFormatter timeFormatter,
            CardImageService cardImages) {
        this.labels = labels;
        this.countryNames = countryNames;
        this.timeFormatter = timeFormatter;
        this.cardImages = cardImages;
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
                toParticipants(viewerFirst(battle.team(), viewerTag), teamResult, battle, viewerTag, locale),
                toParticipants(battle.opponent(), teamResult.opposite(), battle, viewerTag, locale));
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
     * URLがあっても画像の置き場所にまだ無いものは、画像が無いのと同じに扱う。
     */
    public CardDetailView toCardDetail(CardsResponse.Card card, Locale locale) {
        CardsResponse.Card.IconUrls icons = card.iconUrls();
        return new CardDetailView(
                card.id(),
                labels.cardName(card.name(), locale),
                card.name(),
                icons == null ? null : icons.medium(),
                icons == null ? null : cardImages.usable(icons.evolutionMedium()),
                icons == null ? null : cardImages.usable(icons.heroMedium()),
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
                topDecksSeasonNotice(usage.finishedSeason(), locale),
                partners);
    }

    /** デッキ集計を前のシーズンの最終順位の上位で集めたときの断り書き。今シーズンのランキングで集めたときは null。 */
    public String topDecksSeasonNotice(YearMonth finishedSeason, Locale locale) {
        return finishedSeason == null ? null
                : labels.message("topDecks.finishedSeason", locale, seasonMonth(finishedSeason));
    }

    /**
     * 集計ファイルにはカードIDとレベルしか無いので、名前・画像・エリクサーはカード一覧から引く。
     * 一覧に無いID(集計後に削除されたカードなど)は、名前の代わりにIDを出す。
     */
    public TopPlayerDeckView toTopPlayerDeck(TopDecks.SampledDeck deck, Map<Integer, CardsResponse.Card> cardsById,
            Locale locale) {
        TopDecks.Player player = deck.player();
        List<CardView> cards = sampledCards(deck, cardsById, true, locale);
        return new TopPlayerDeckView(player.rank(), player.name(), Tags.toPathSegment(player.tag()), player.rating(),
                cards, sampledSupportCards(deck, cardsById, true, locale),
                sampledDeckMeta(deck, cardsById, toNullable(Deck.averageLevel(player.levels()))));
    }

    /** 人気のデッキ。レベルは人ごとに違うので出さない(平均レベルも)。 */
    public PopularDeckView toPopularDeck(PopularDeck popular, Map<Integer, CardsResponse.Card> cardsById,
            Locale locale) {
        TopDecks.SampledDeck deck = popular.best();
        TopDecks.Player player = deck.player();
        return new PopularDeckView(popular.players(), player.rank(), player.name(), Tags.toPathSegment(player.tag()),
                sampledCards(deck, cardsById, false, locale), sampledSupportCards(deck, cardsById, false, locale),
                sampledDeckMeta(deck, cardsById, null), deck.cardIds());
    }

    private List<CardView> sampledCards(TopDecks.SampledDeck deck, Map<Integer, CardsResponse.Card> cardsById,
            boolean withLevels, Locale locale) {
        TopDecks.Player player = deck.player();
        List<CardView> cards = new ArrayList<>();
        for (int i = 0; i < deck.cardIds().size(); i++) {
            int id = deck.cardIds().get(i);
            int level = withLevels && i < player.levels().size() ? player.levels().get(i) : 0;
            cards.add(toCardView(id, cardsById.get(id), level, player.formAt(i), false, locale));
        }
        return cards;
    }

    private List<CardView> sampledSupportCards(TopDecks.SampledDeck deck,
            Map<Integer, CardsResponse.Card> cardsById, boolean withLevels, Locale locale) {
        Integer towerLevel = deck.player().towerLevel();
        return deck.towerTroopId() == null ? List.of()
                : List.of(toCardView(deck.towerTroopId(), cardsById.get(deck.towerTroopId()),
                        withLevels && towerLevel != null ? towerLevel : 0, CardForm.NORMAL, true, locale));
    }

    private static DeckMetaView sampledDeckMeta(TopDecks.SampledDeck deck,
            Map<Integer, CardsResponse.Card> cardsById, Double averageLevel) {
        List<Integer> elixirCosts = deck.cardIds().stream()
                .map(id -> cardsById.get(id) == null ? null : cardsById.get(id).elixirCost())
                .toList();
        OptionalInt cycle = Deck.fourCardCycle(elixirCosts);
        return new DeckMetaView(
                toNullable(Deck.averageElixir(elixirCosts)),
                cycle.isPresent() ? cycle.getAsInt() : null,
                averageLevel,
                Deck.copyUrl(deck.cardIds(), deck.towerTroopId()).orElse(null));
    }

    /** 同じ8枚がいなければ「〇〇以外の7枚が同じ」のように、違うカードの名前を添える。 */
    public SimilarDecksView toSimilarDecks(SimilarDecks similar, List<Integer> deck,
            Map<Integer, CardsResponse.Card> cardsById, Locale locale) {
        String text;
        if (similar.sharedCards() == Deck.SIZE) {
            text = labels.message("deck.similar.same", locale, similar.sampleSize(), similar.players());
        } else {
            List<String> others = deck.stream()
                    .filter(id -> !similar.cardIds().contains(id))
                    .map(id -> cardsById.containsKey(id) ? labels.cardName(cardsById.get(id).name(), locale)
                            : String.valueOf(id))
                    .toList();
            text = labels.message("deck.similar.partial", locale, similar.sampleSize(), similar.players(),
                    similar.sharedCards(), ListFormatter.getInstance(ULocale.forLocale(locale)).format(others));
        }
        return new SimilarDecksView(text, similar.cardIds(), similar.selfExcluded());
    }

    /** 記録の間がこれより空いたら、線を切る(1000位より下にいた間と、記録が止まっていた間)。 */
    static final Duration RANK_HISTORY_GAP = Duration.ofMinutes(90);

    /** @param points 古い順で2点以上 */
    public RankHistoryView toRankHistory(List<SeasonRankLog.Point> points, Locale locale) {
        long from = points.getFirst().at().getEpochSecond();
        long to = points.getLast().at().getEpochSecond();
        int[] axis = rankAxis(points.stream().mapToInt(SeasonRankLog.Point::rank).min().orElseThrow(),
                points.stream().mapToInt(SeasonRankLog.Point::rank).max().orElseThrow());
        int top = axis[0];
        int bottom = axis[1];
        int step = axis[2];

        List<String> segments = new ArrayList<>();
        StringBuilder segment = new StringBuilder();
        SeasonRankLog.Point previous = null;
        int inSegment = 0;
        for (SeasonRankLog.Point point : points) {
            if (previous != null && Duration.between(previous.at(), point.at()).compareTo(RANK_HISTORY_GAP) > 0) {
                segments.add(closeSegment(segment, inSegment));
                segment.setLength(0);
                inSegment = 0;
            }
            String xy = svgCoordinate(ratio(point.at().getEpochSecond(), from, to)) + ","
                    + svgCoordinate(ratio(point.rank(), top, bottom));
            segment.append(inSegment == 0 ? "" : " ").append(xy);
            inSegment++;
            previous = point;
        }
        segments.add(closeSegment(segment, inSegment));

        List<Integer> tickRanks = new ArrayList<>(List.of(top));
        for (int rank = (top / step + 1) * step; rank <= bottom; rank += step) {
            tickRanks.add(rank);
        }
        NumberFormat numberFormat = NumberFormat.getIntegerInstance(locale);
        List<RankHistoryView.Tick> yTicks = tickRanks.stream()
                .map(rank -> new RankHistoryView.Tick(percent(ratio(rank, top, bottom)),
                        svgCoordinate(ratio(rank, top, bottom)), numberFormat.format(rank)))
                .toList();

        DateFormat dayFormat = DateFormat.getInstanceForSkeleton("Md", ULocale.forLocale(locale));
        dayFormat.setTimeZone(TimeZone.GMT_ZONE);
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate day = LocalDate.ofInstant(points.getFirst().at(), ZoneOffset.UTC).plusDays(1);
                !day.atStartOfDay(ZoneOffset.UTC).toInstant().isAfter(points.getLast().at()); day = day.plusDays(1)) {
            days.add(day);
        }
        int every = Math.max(1, (days.size() + RANK_HISTORY_MAX_DAY_TICKS - 1) / RANK_HISTORY_MAX_DAY_TICKS);
        List<RankHistoryView.Tick> xTicks = new ArrayList<>();
        for (int i = days.size() - 1; i >= 0; i -= every) {
            Instant midnight = days.get(i).atStartOfDay(ZoneOffset.UTC).toInstant();
            double x = ratio(midnight.getEpochSecond(), from, to);
            xTicks.addFirst(new RankHistoryView.Tick(percent(x), svgCoordinate(x),
                    dayFormat.format(Date.from(midnight))));
        }

        SeasonRankLog.Point last = points.getLast();
        SeasonRankLog.Point best = points.stream().min(Comparator.comparingInt(SeasonRankLog.Point::rank)).orElseThrow();
        double endY = ratio(last.rank(), top, bottom);
        StringBuilder json = new StringBuilder("[");
        for (SeasonRankLog.Point point : points) {
            json.append(json.length() == 1 ? "" : ",").append('[').append(point.at().getEpochSecond()).append(',')
                    .append(point.rank()).append(',').append(point.rating()).append(']');
        }
        json.append(']');
        return new RankHistoryView(segments, yTicks, xTicks, percent(1.0), percent(endY),
                labels.message("player.ranked.rank", locale, last.rank()), endY < 0.2,
                labels.message("rankHistory.best", locale, best.rank(), dayFormat.format(Date.from(best.at()))),
                json.toString(), labels.message("rankHistory.point", locale, "{rank}", "{rating}"), top, bottom);
    }

    static final int RANK_HISTORY_MAX_DAY_TICKS = 6;

    /**
     * 縦軸の上端・下端の順位と目盛りの間隔。目盛りが3〜5本ほどになるよう、間隔を1・2・5の10のべき乗倍から選ぶ。
     * 上端は1位か間隔の倍数、下端は間隔の倍数にそろえる。
     *
     * @return {上端, 下端, 間隔}
     */
    static int[] rankAxis(int best, int worst) {
        int step = 1;
        while ((worst - best) / (double) step > 4) {
            int magnitude = step;
            step = switch (Integer.toString(step).charAt(0)) {
                case '1' -> magnitude * 2;
                case '2' -> magnitude / 2 * 5;
                default -> magnitude * 2;
            };
        }
        int top = Math.max(1, best / step * step);
        int bottom = (worst + step - 1) / step * step;
        if (bottom <= top) {
            bottom = top + step;
        }
        return new int[] {top, bottom, step};
    }

    private static double ratio(double value, double from, double to) {
        return to == from ? 0 : (value - from) / (to - from);
    }

    private static String percent(double ratio) {
        return String.format(Locale.ROOT, "%.2f", ratio * 100);
    }

    private static String svgCoordinate(double ratio) {
        return String.format(Locale.ROOT, "%.1f", ratio * 1000);
    }

    /** 1点だけの区間は、同じ点を2つ並べる(線の端の丸で点として見える)。 */
    private static String closeSegment(StringBuilder segment, int points) {
        return points == 1 ? segment + " " + segment : segment.toString();
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

    /** 進化・ヒーローの画像が無い(置き場所にまだ無いものを含む)カードは通常の画像にする。 */
    private String iconFor(CardForm form, String medium, String evolution, String hero) {
        String url = switch (form) {
            case EVOLUTION -> cardImages.usable(evolution);
            case HERO -> cardImages.usable(hero);
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

    /**
     * 選んだカードごとに1つずつ選択欄を出し、まだ選べる間は「＋」で開く空の欄を1つ足す。
     * 1枚も選んでいなければ、空の欄1つだけ(最初から出しておく)。
     */
    public List<DeckFilterRowView> toDeckFilterRows(List<Integer> selected, int maxCards) {
        List<DeckFilterRowView> rows = new ArrayList<>();
        for (Integer cardId : selected) {
            List<Integer> others = selected.stream().filter(id -> !id.equals(cardId)).toList();
            rows.add(new DeckFilterRowView(cardId, others, Set.copyOf(others), false));
        }
        if (selected.size() < maxCards) {
            rows.add(new DeckFilterRowView(null, selected, Set.copyOf(selected), !selected.isEmpty()));
        }
        return rows;
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
                            group.cards().stream().map(card -> toCatalogItem(card, tower, null, locale)).toList());
                })
                .toList();
    }

    /** @param groups {@link CardService#byUsage} の結果(通常のカードの1グループと、タワーユニットのグループ) */
    public List<CardCatalogGroupView> toCardUsageRanking(List<CardService.CardGroup> groups, CardUsage usage,
            Locale locale) {
        return groups.stream()
                .map(group -> {
                    boolean tower = group.rarity() == null;
                    return new CardCatalogGroupView(
                            labels.message(tower ? "cards.group.tower" : "cards.group.cards", locale),
                            group.cards().stream().map(card -> toCatalogItem(card, tower, usage, locale)).toList());
                })
                .toList();
    }

    /** @param usage 使用率順で出すときだけ渡す */
    private CardCatalogItemView toCatalogItem(CardsResponse.Card card, boolean tower, CardUsage usage, Locale locale) {
        String name = labels.cardName(card.name(), locale);
        CardUsage.Usage cardUsage = usage == null ? null : usage.usageOf(card.id());
        return new CardCatalogItemView(card.id(), name,
                searchText(name, card.name(), labels.cardAlias(card.name(), locale)),
                card.iconUrls() == null ? null : card.iconUrls().medium(),
                toElixirBadge(card.elixirCost(), tower, locale), card.elixirCost(),
                cardUsage == null ? null : Math.round(cardUsage.percent() * 10) / 10.0,
                cardUsage == null || cardUsage.rank() == 0 ? null : cardUsage.rank());
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
        return new BattleStatsView(stats.total(), stats.wins(), stats.losses(), stats.draws(), stats.winRatePercent(),
                stats.friendlyExcluded(),
                stats.boatDefenseExcluded(), stats.cardRanking(), toPerformances(stats.favoriteCards(), locale),
                toPerformances(stats.weakCards(), locale));
    }

    public List<ClanMemberView> toMembers(List<ClanResponse.Member> members, Locale locale) {
        // 一覧内で基準時刻がずれないよう、1回だけ現在時刻を取る。
        Instant now = Instant.now();
        return members.stream()
                .map(member -> {
                    OptionalLong days = MemberActivity.inactiveDays(member.lastSeen(), now);
                    return new ClanMemberView(member.clanRank() > 0 ? member.clanRank() : null,
                            Tags.toPathSegment(member.tag()), GameText.stripFormatting(member.name()),
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
        return toPlayerRanking(ranking, null, locale);
    }

    /**
     * dayAgo は約24時間前の世界ランキングの記録。渡したときだけ、今シーズンの順位なら24時間の変化の列を付ける
     * (終わったシーズンの最終順位を代わりに出しているときは、比べる意味が無いので付けない)。
     */
    public PlayerRankingView toPlayerRanking(PlayerRanking ranking, RankingSnapshot dayAgo, Locale locale) {
        String notice = switch (ranking.status()) {
            case CURRENT_SEASON -> null;
            case NEW_SEASON -> labels.message("playerRanking.newSeason", locale, ranking.players().size());
            case FINISHED_SEASON -> labels.message("playerRanking.finishedSeason", locale,
                    seasonMonth(ranking.finishedSeason()));
            case EMPTY -> labels.message("playerRanking.empty", locale);
            case UNAVAILABLE -> labels.message("ranking.unavailable", locale);
        };
        boolean showChange = dayAgo != null && (ranking.status() == PlayerRanking.Status.CURRENT_SEASON
                || ranking.status() == PlayerRanking.Status.NEW_SEASON);
        Map<String, RankingSnapshot.Entry> previous = showChange ? dayAgo.byTag() : Map.of();
        return new PlayerRankingView(ranking.players().stream()
                .map(player -> toPlayerRankingRow(player, showChange
                        ? toRankChange(RankChange.of(player.rank(), rankOf(previous.get(player.tag()))), locale)
                        : null))
                .toList(), notice, showChange);
    }

    private static Integer rankOf(RankingSnapshot.Entry entry) {
        return entry == null ? null : entry.rank();
    }

    private RankChangeView toRankChange(RankChange change, Locale locale) {
        NumberFormat numbers = NumberFormat.getIntegerInstance(locale);
        return switch (change.kind()) {
            case UP -> new RankChangeView("▲" + numbers.format(change.places()),
                    labels.message("playerRanking.change.up", locale, change.places()), "up");
            case DOWN -> new RankChangeView("▼" + numbers.format(change.places()),
                    labels.message("playerRanking.change.down", locale, change.places()), "down");
            case SAME -> new RankChangeView("－", labels.message("playerRanking.change.same", locale), "same");
            case NEW -> new RankChangeView("NEW",
                    labels.message("playerRanking.change.new", locale, RankingService.MAX_PLAYER_RANKING_SIZE), "new");
        };
    }

    // 月名は言語ごとにICUに書かせる(引数は日時)。サーバーのタイムゾーンで前後の月にずれないよう、月の半ばにする。
    private static Date seasonMonth(YearMonth season) {
        return Date.from(season.atDay(15).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    private static PlayerRankingRowView toPlayerRankingRow(PlayerRankingResponse.RankedPlayer player,
            RankChangeView change) {
        return new PlayerRankingRowView(player.rank(), Tags.toPathSegment(player.tag()), player.tag(),
                GameText.stripFormatting(player.name()), player.eloRating(),
                player.clan() == null ? null : GameText.stripFormatting(player.clan().name()),
                player.clan() == null ? null : Tags.toPathSegment(player.clan().tag()), change);
    }

    public RankingReportView toRankingReport(RankingHistoryService.Report report, Locale locale) {
        RankingMovements movements = report.movements();
        RankingSnapshot before = movements.before();
        NumberFormat numbers = NumberFormat.getIntegerInstance(locale);
        return new RankingReportView(
                timeFormatter.instant(movements.after().takenAt(), locale),
                before == null ? null : timeFormatter.instant(before.takenAt(), locale),
                movements.after().entries().size(),
                movements.top().stream().map(entry -> toTopRow(entry, before != null, numbers, locale)).toList(),
                report.leaderChanges().stream()
                        .map(change -> new RankingReportView.LeaderChangeRow(
                                timeFormatter.instant(change.at(), locale), toLink(change.leader()),
                                toLink(change.previous())))
                        .toList(),
                movements.climbers().stream()
                        .map(climber -> new RankingReportView.ClimberRow(toLink(climber.entry()),
                                climber.previousRank(), climber.entry().rank(),
                                signed(climber.ratingGain(), numbers)))
                        .toList(),
                movements.dropouts().stream()
                        .map(dropout -> new RankingReportView.DropoutRow(toLink(dropout.previous()),
                                dropout.previous().rank(),
                                dropout.current() == null ? null : dropout.current().rank()))
                        .toList());
    }

    public List<SeasonLinkView> toSeasonLinks(List<YearMonth> seasons, Locale locale) {
        return seasons.stream().map(season -> toSeasonLink(season, locale)).toList();
    }

    public SeasonLinkView toSeasonLink(YearMonth season, Locale locale) {
        return new SeasonLinkView(season.toString(), labels.message("season.name", locale, seasonMonth(season)));
    }

    /** 終わったシーズンの最終順位の表。断り書きも24時間の変化も無い。 */
    public PlayerRankingView toFinalStandings(List<PlayerRankingResponse.RankedPlayer> players) {
        return new PlayerRankingView(players.stream().map(player -> toPlayerRankingRow(player, null)).toList(),
                null, false);
    }

    public List<DayLinkView> toDayLinks(List<LocalDate> days, Locale locale) {
        return days.stream()
                .map(day -> new DayLinkView(day, labels.message("movements.archive.day", locale,
                        Date.from(day.atTime(12, 0).toInstant(ZoneOffset.UTC)))))
                .toList();
    }

    private RankingReportView.TopRow toTopRow(RankingMovements.TopEntry entry, boolean compared,
            NumberFormat numbers, Locale locale) {
        RankingSnapshot.Entry now = entry.entry();
        String change = null;
        String changeClass = null;
        if (compared && entry.previousRank() == null) {
            change = labels.message("movements.change.new", locale);
            changeClass = "new";
        } else if (compared) {
            int moved = entry.previousRank() - now.rank();
            change = moved > 0 ? "↑" + numbers.format(moved) : moved < 0 ? "↓" + numbers.format(-moved) : "–";
            changeClass = moved > 0 ? "up" : moved < 0 ? "down" : "same";
        }
        Integer ratingChange = entry.ratingChange();
        return new RankingReportView.TopRow(now.rank(), toLink(now), now.rating(), change, changeClass,
                ratingChange == null ? null : signed(ratingChange, numbers),
                ratingChange == null ? null : ratingChange > 0 ? "up" : ratingChange < 0 ? "down" : "same");
    }

    private static PlayerLinkView toLink(RankingSnapshot.Entry entry) {
        return new PlayerLinkView(entry.name(), Tags.toPathSegment(entry.tag()));
    }

    private static String signed(int value, NumberFormat numbers) {
        return value > 0 ? "+" + numbers.format(value) : value == 0 ? "±0" : numbers.format(value);
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

    /** details は候補のタグをキーにした今のプロフィール(取っていない人は入っていない)。 */
    public List<PlayerNameMatchView> toPlayerNameMatches(List<PlayerNameMatch> players,
            Map<String, PlayerResponse> details, Locale locale) {
        return players.stream()
                .map(player -> new PlayerNameMatchView(GameText.stripFormatting(player.name()), player.tag(),
                        Tags.toPathSegment(player.tag()),
                        player.lastSeen() == null ? null : timeFormatter.instant(player.lastSeen(), locale),
                        Optional.ofNullable(details.get(player.tag())).map(this::toProfile).orElse(null)))
                .toList();
    }

    private PlayerProfileView toProfile(PlayerResponse player) {
        PlayerResponse.RankedSeasonResult ranked = player.currentPathOfLegendSeasonResult();
        return new PlayerProfileView(player.trophies(), kingTowerLevel(player),
                player.clan() == null ? null : GameText.stripFormatting(player.clan().name()),
                ranked != null && ranked.hasRank() ? ranked.rank() : null);
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
                toOpponents(battle.opponent(), locale),
                toLinks(battle.team().stream().filter(p -> !isViewer(p, viewerTag)).toList()),
                battle.team().stream().filter(p -> isViewer(p, viewerTag)).findFirst()
                        .map(viewer -> toTrophyChange(battle, viewer, locale)).orElse(null),
                battle.team().stream().filter(p -> isViewer(p, viewerTag)).findFirst()
                        .map(viewer -> toCards(viewer.cards(), false, locale)).orElse(List.of()));
    }

    private TrophyChangeView toTrophyChange(BattleLogEntry battle, BattleLogEntry.Participant participant, Locale locale) {
        return TrophyChange.of(battle.type(), participant.trophyChange(), battle.leagueNumber())
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
    private List<OpponentView> toOpponents(List<BattleLogEntry.Participant> participants, Locale locale) {
        boolean teamBattle = participants.size() > 1;
        return participants.stream()
                .map(p -> new OpponentView(GameText.stripFormatting(p.name()), Tags.toPathSegment(p.tag()),
                        teamBattle ? null : toNullable(Deck.averageLevel(inGameLevels(p.cards()))),
                        toCards(p.cards(), false, locale)))
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
            BattleLogEntry battle, String viewerTag, Locale locale) {
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
                        toTrophyChange(battle, participant, locale)))
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
        String mode = labels.gameMode(battle.type(), battle.gameMode() != null ? battle.gameMode().name() : null, locale);
        if ("pathOfLegend".equals(battle.type())) {
            OptionalInt league = RankedLeague.of(battle.leagueNumber());
            if (league.isPresent()) {
                return labels.message("battlelog.modeWithLeague", locale, mode, leagueName(league.getAsInt(), locale));
            }
        }
        return mode;
    }

    private String leagueName(int league, Locale locale) {
        return labels.message("ranked.league." + league, locale);
    }

    /** 世界の順位がある人(上の段に順位とレーティングを出す)と、リーグを出せない人は null。 */
    public RankedLeagueView toCurrentRankedLeague(PlayerResponse player, Locale locale) {
        PlayerResponse.RankedSeasonResult ranked = player.currentPathOfLegendSeasonResult();
        if (ranked == null || ranked.hasRank()) {
            return null;
        }
        OptionalInt league = RankedLeague.currentOf(ranked.leagueNumber(), player.trophies());
        if (league.isEmpty()) {
            return null;
        }
        Integer rating = league.getAsInt() == RankedLeague.TOP && ranked.trophies() != null && ranked.trophies() > 0
                ? ranked.trophies() : null;
        return new RankedLeagueView(leagueName(league.getAsInt(), locale), rating);
    }
}
