package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.client.dto.ClanRankingResponse;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.config.IcuMessageSource;
import com.example.clashroyaleapi.domain.CardCollection;
import com.example.clashroyaleapi.domain.CardForm;
import com.example.clashroyaleapi.domain.FavoriteFetch;
import com.example.clashroyaleapi.domain.TopDecks;
import com.example.clashroyaleapi.service.CardService;
import com.example.clashroyaleapi.service.PlayerRanking;
import com.example.clashroyaleapi.web.view.BattleDetailView;
import com.example.clashroyaleapi.web.view.CardCatalogGroupView;
import com.example.clashroyaleapi.web.view.CardCollectionView;
import com.example.clashroyaleapi.web.view.CardOptionView;
import com.example.clashroyaleapi.web.view.CardView;
import com.example.clashroyaleapi.web.view.ClanJoinView;
import com.example.clashroyaleapi.web.view.ClanRankingRowView;
import com.example.clashroyaleapi.web.view.BattleSummaryView;
import com.example.clashroyaleapi.web.view.FavoriteClanView;
import com.example.clashroyaleapi.web.view.FavoritePlayerView;
import com.example.clashroyaleapi.web.view.OpponentView;
import com.example.clashroyaleapi.web.view.ParticipantView;
import com.example.clashroyaleapi.web.view.PlayerLinkView;
import com.example.clashroyaleapi.web.view.PlayerProgressView;
import com.example.clashroyaleapi.web.view.TopPlayerDeckView;
import com.example.clashroyaleapi.web.view.TrophyChangeView;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 2v2で「自分」と「味方」を取り違えない並べ方を中心に検証する。ラベル解決はモックで素通しにする。 */
class ViewMapperTest {

    private LabelResolver labels;
    private ViewMapper viewMapper;

    @BeforeEach
    void setUp() {
        labels = mock(LabelResolver.class);
        when(labels.gameMode(anyString(), anyString(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(labels.cardName(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(labels.rarity(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        viewMapper = new ViewMapper(labels, mock(CountryNames.class), mock(TimeFormatter.class));
    }

    @Test
    void カードコレクションは最大レベルの割合を四捨五入して求める() {
        CardCollection collection = CardCollection.of(List.of(
                new PlayerResponse.OwnedCard(1, "Knight", 16, 16, 1, "common"),
                new PlayerResponse.OwnedCard(2, "Skeletons", 10, 16, 1, "common"),
                new PlayerResponse.OwnedCard(3, "Archers", 16, 16, 1, "common")));

        CardCollectionView view = viewMapper.toCardCollection(collection, Locale.JAPANESE);

        assertEquals(3, view.totalCards());
        assertEquals(2, view.maxedCards());
        assertEquals(67, view.maxedPercent());
        assertEquals(1, view.rarities().size());
        assertEquals("common", view.rarities().get(0).rarityLabel());
        assertEquals(67, view.rarities().get(0).maxedPercent());
    }

    @Test
    void 対戦詳細では見ているプレイヤーを先頭にし味方と区別する() {
        // 公式APIのteamは、見ているプレイヤーが先頭とは限らない(実データで味方が先頭の2v2があった)。
        BattleLogEntry duel = duel(List.of(participant("#MATE", "Mate"), participant("#VIEWER", "Viewer")));

        BattleDetailView detail = viewMapper.toBattleDetail(duel, "viewer", Locale.JAPANESE);

        List<ParticipantView> team = detail.team();
        assertEquals("Viewer", team.get(0).name());
        assertTrue(team.get(0).viewer());
        assertEquals("Mate", team.get(1).name());
        assertFalse(team.get(1).viewer());
        assertTrue(detail.opponents().stream().noneMatch(ParticipantView::viewer));
    }

    @Test
    void 対戦履歴では相手を全員出し見ているプレイヤー以外を味方にする() {
        BattleLogEntry duel = duel(List.of(participant("#MATE", "Mate"), participant("#VIEWER", "Viewer")));

        BattleSummaryView summary = viewMapper.toBattleSummaries(List.of(duel), "#VIEWER", Locale.JAPANESE).get(0);

        assertEquals(List.of("Opp1", "Opp2"), summary.opponents().stream().map(OpponentView::name).toList());
        assertEquals(List.of("Mate"), summary.teammates().stream().map(PlayerLinkView::name).toList());
    }

    @Test
    void 表示する名前からは色指定タグを取り除く() {
        BattleLogEntry battle = new BattleLogEntry("PvP", "20260101T000000.000Z",
                new BattleLogEntry.GameMode("Ladder"),
                List.of(participant("#VIEWER", "Viewer")), List.of(participant("#OPP1", "<c6>Ale :D")), null);

        BattleSummaryView summary = viewMapper.toBattleSummaries(List.of(battle), "#VIEWER", Locale.JAPANESE).get(0);

        assertEquals("Ale :D", summary.opponents().get(0).name());
        assertTrue(summary.teammates().isEmpty());
    }

    @Test
    void キングタワーレベルが0なら出さない() {
        PlayerResponse active = new PlayerResponse("#AAA", "a", 13, 0, 0, 0, 0, 0, null, List.of(), List.of(), null,
                null, List.of());
        PlayerResponse dormant = new PlayerResponse("#BBB", "b", 0, 0, 0, 0, 0, 0, null, List.of(), List.of(), null,
                null, List.of());

        assertEquals(13, viewMapper.kingTowerLevel(active));
        assertNull(viewMapper.kingTowerLevel(dormant));
    }

    @Test
    void 船のバトルの守備側は集計から外していると分かる名前で出す() {
        when(labels.message("battletype.boatDefense", Locale.JAPANESE)).thenReturn("クラン対戦(船の防衛)");
        BattleLogEntry defense = boatBattle("defender");
        BattleLogEntry attack = boatBattle("attacker");

        List<BattleSummaryView> summaries =
                viewMapper.toBattleSummaries(List.of(defense, attack), "#VIEWER", Locale.JAPANESE);

        assertEquals("クラン対戦(船の防衛)", summaries.get(0).gameMode());
        assertEquals("ClanWar_BoatBattle", summaries.get(1).gameMode());
    }

    /** APIのlevelはレアリティごとに数え直した値なので、ゲーム内表記に直してから平均する。 */
    @Test
    void 対戦相手の平均レベルはゲーム内表記で求める() {
        List<BattleLogEntry.Card> deck = List.of(
                card(14, 16), card(14, 16), card(14, 16), card(14, 16),
                card(12, 14), card(12, 14), card(9, 11), card(5, 8));
        BattleLogEntry battle = new BattleLogEntry("PvP", "20260101T000000.000Z",
                new BattleLogEntry.GameMode("Ladder"), List.of(participant("#VIEWER", "Viewer")),
                List.of(new BattleLogEntry.Participant("#OPP1", "Opp1", 0, deck, List.of(), null)), null);

        BattleSummaryView summary = viewMapper.toBattleSummaries(List.of(battle), "#VIEWER", Locale.JAPANESE).get(0);

        // ゲーム内表記ではコモン14・レア14・エピック14・レジェンダリー13。生値の平均(11.75)にならないこと。
        assertEquals(13.875, summary.opponents().get(0).averageLevel());
    }

    @Test
    void 対戦履歴には見ているプレイヤーのトロフィー増減を符号付きで出す() {
        when(labels.message(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        BattleLogEntry ladder = new BattleLogEntry("PvP", "20260101T000000.000Z", new BattleLogEntry.GameMode("Ladder"),
                List.of(new BattleLogEntry.Participant("#VIEWER", "Viewer", 1, List.of(), List.of(), -1030)),
                List.of(new BattleLogEntry.Participant("#OPP", "Opp", 3, List.of(), List.of(), 1030)), null);
        BattleLogEntry friendly = new BattleLogEntry("friendly", "20260101T000000.000Z",
                new BattleLogEntry.GameMode("Friendly"), List.of(participant("#VIEWER", "Viewer")),
                List.of(participant("#OPP", "Opp")), null);

        List<BattleSummaryView> summaries = viewMapper.toBattleSummaries(List.of(ladder, friendly), "#VIEWER",
                Locale.JAPANESE);

        assertEquals(new TrophyChangeView("battlelog.change.trophies", "-1,030", false), summaries.get(0).trophyChange());
        assertNull(summaries.get(1).trophyChange());
        assertEquals(new TrophyChangeView("battlelog.change.trophies", "+1,030", true),
                viewMapper.toBattleDetail(ladder, "#VIEWER", Locale.JAPANESE).opponents().get(0).trophyChange());
    }

    @Test
    void 八枚そろっていないデッキの相手は平均レベルを出さない() {
        BattleLogEntry battle = new BattleLogEntry("PvP", "20260101T000000.000Z",
                new BattleLogEntry.GameMode("Ladder"),
                List.of(participant("#VIEWER", "Viewer")), List.of(participant("#OPP1", "Opp1")), null);

        BattleSummaryView summary = viewMapper.toBattleSummaries(List.of(battle), "#VIEWER", Locale.JAPANESE).get(0);

        assertNull(summary.opponents().get(0).averageLevel());
    }

    @Test
    void 二対二の相手は八枚そろっていても平均レベルを出さない() {
        List<BattleLogEntry.Card> deck = List.of(
                card(9, 16), card(9, 16), card(9, 16), card(9, 16),
                card(7, 14), card(7, 14), card(4, 11), card(1, 8));
        BattleLogEntry battle = new BattleLogEntry("PvP", "20260101T000000.000Z",
                new BattleLogEntry.GameMode("TeamVsTeam"),
                List.of(participant("#VIEWER", "Viewer"), participant("#MATE", "Mate")),
                List.of(new BattleLogEntry.Participant("#OPP1", "Opp1", 0, deck, List.of(), null),
                        new BattleLogEntry.Participant("#OPP2", "Opp2", 0, deck, List.of(), null)), null);

        BattleSummaryView summary = viewMapper.toBattleSummaries(List.of(battle), "#VIEWER", Locale.JAPANESE).get(0);

        assertTrue(summary.opponents().stream().allMatch(o -> o.averageLevel() == null));
    }

    @Test
    void クランランキングは対象クランだけ対戦トロフィーを持つ() {
        ClanRankingResponse.RankedClan withTrophies = new ClanRankingResponse.RankedClan("#A", "ClanA", 1, 140000, 50,
                null);
        ClanRankingResponse.RankedClan withoutTrophies = new ClanRankingResponse.RankedClan("#B", "ClanB", 2, 140000,
                50, null);

        List<ClanRankingRowView> rows = viewMapper.toClanRankingRows(List.of(withTrophies, withoutTrophies),
                Map.of("#A", 2196), Locale.JAPANESE);

        assertEquals(2196, rows.get(0).clanWarTrophies());
        assertNull(rows.get(1).clanWarTrophies());
    }

    @Test
    void エリクサーバッジは鏡が疑問符タワーユニットは無し() {
        List<CardCatalogGroupView> groups = viewMapper.toCardCatalog(List.of(
                new CardService.CardGroup("epic", List.of(
                        new CardsResponse.Card(1, "Mirror", 14, null, null, "epic", null),
                        new CardsResponse.Card(2, "Giant", 14, null, 5, "epic", null))),
                new CardService.CardGroup(null, List.of(
                        new CardsResponse.Card(9, "Tower Princess", 14, null, null, "common", null)))),
                Locale.JAPANESE);

        assertEquals("?", groups.get(0).cards().get(0).elixir().text());
        assertNull(groups.get(0).cards().get(0).elixirCost());
        assertEquals("5", groups.get(0).cards().get(1).elixir().text());
        assertNull(groups.get(1).cards().get(0).elixir());
    }

    @Test
    void カードの選択肢は読み仮名の五十音順に並べる() {
        Map<String, String> japanese = Map.of("Cannon", "大砲", "Knight", "ナイト", "Archers", "アーチャー");
        Map<String, String> readings = Map.of("Cannon", "タイホウ");
        when(labels.cardName(anyString(), any())).thenAnswer(invocation -> japanese.get(invocation.<String>getArgument(0)));
        when(labels.cardSortKey(anyString(), any())).thenAnswer(invocation -> readings.getOrDefault(
                invocation.<String>getArgument(0), japanese.get(invocation.<String>getArgument(0))));

        List<String> names = viewMapper.toCardOptions(List.of(
                        new CardsResponse.Card(1, "Cannon", 14, null, 3, "common", null),
                        new CardsResponse.Card(2, "Knight", 16, null, 3, "common", null),
                        new CardsResponse.Card(3, "Archers", 16, null, 3, "common", null)),
                Locale.JAPANESE).stream().map(CardOptionView::name).toList();

        // 読みが無いと漢字の「大砲」は仮名の後ろ(ナイトの後)に回る。
        assertEquals(List.of("アーチャー", "大砲", "ナイト"), names);
    }

    @Test
    void カード一覧の絞り込み用の文字列は表示名と英語名と通称を含む() {
        when(labels.cardAlias("P.E.K.K.A", Locale.JAPANESE)).thenReturn("ペッカ");
        List<CardCatalogGroupView> groups = viewMapper.toCardCatalog(List.of(
                new CardService.CardGroup("epic", List.of(
                        new CardsResponse.Card(1, "P.E.K.K.A", 11, null, 7, "epic", null),
                        new CardsResponse.Card(2, "Knight", 16, null, 3, "common", null)))),
                Locale.JAPANESE);

        // 表示名はモックが英語名を素通しにしている。
        assertEquals("P.E.K.K.A P.E.K.K.A ペッカ", groups.get(0).cards().get(0).searchText());
        assertEquals("Knight Knight", groups.get(0).cards().get(1).searchText());
    }

    @Test
    void お気に入りプレイヤーは見つかった順位無し見つからない取得できないを区別する() {
        PlayerResponse.RankedSeasonResult ranked = new PlayerResponse.RankedSeasonResult(1, 4500, 12);
        PlayerResponse found = new PlayerResponse("#AAA", "太郎", 10, 5000, 5000, 1, 0, 0,
                new PlayerResponse.ClanRef("#CLAN", "償い"), List.of(), List.of(), ranked, null, List.of());
        PlayerResponse noRank = new PlayerResponse("#BBB", "次郎", 5, 1000, 1000, 0, 0, 0, null, List.of(), List.of(),
                new PlayerResponse.RankedSeasonResult(null, 0, null), null, List.of());

        List<FavoritePlayerView> rows = viewMapper.toFavoritePlayerRows(List.of(
                new FavoriteFetch.Found<>("AAA", "太郎(旧)", found),
                new FavoriteFetch.Found<>("BBB", "次郎", noRank),
                new FavoriteFetch.NotFound<>("CCC", "三郎"),
                new FavoriteFetch.Unavailable<>("DDD", "四郎")));

        assertTrue(rows.get(0).found());
        assertEquals(4500, rows.get(0).rankedRating());
        assertEquals("償い", rows.get(0).clanName());
        assertTrue(rows.get(1).found());
        assertNull(rows.get(1).rankedRating());
        assertFalse(rows.get(2).found());
        assertTrue(rows.get(2).notFound());
        assertEquals("三郎", rows.get(2).name());
        assertFalse(rows.get(3).found());
        assertFalse(rows.get(3).notFound());
    }

    @Test
    void 前回からの変化の元の値はランク戦の順位が無ければ順位もレーティングも持たない() {
        PlayerResponse ranked = new PlayerResponse("#AAA", "太郎", 10, 14000, 14000, 900, 400, 100, null,
                List.of(), List.of(), new PlayerResponse.RankedSeasonResult(7, 3100, 25), null, List.of());
        PlayerResponse noRank = new PlayerResponse("#BBB", "次郎", 5, 1000, 1000, 30, 20, 2, null,
                List.of(), List.of(), new PlayerResponse.RankedSeasonResult(3, 0, null), null, List.of());
        PlayerResponse noResult = new PlayerResponse("#CCC", "三郎", 1, 0, 0, 0, 0, 0, null,
                List.of(), List.of(), null, null, List.of());
        // シーズンの切り替え直後は、レーティングが0に戻っても前のシーズンの順位が残る。
        PlayerResponse justReset = new PlayerResponse("#DDD", "四郎", 16, 14000, 14000, 900, 400, 100, null,
                List.of(), List.of(), new PlayerResponse.RankedSeasonResult(1, 0, 1), null, List.of());

        assertEquals(new PlayerProgressView(14000, 900, 400, 25, 3100), viewMapper.toPlayerProgress(ranked));
        assertEquals(new PlayerProgressView(1000, 30, 20, null, null), viewMapper.toPlayerProgress(noRank));
        assertEquals(new PlayerProgressView(0, 0, 0, null, null), viewMapper.toPlayerProgress(noResult));
        assertEquals(new PlayerProgressView(14000, 900, 400, null, null), viewMapper.toPlayerProgress(justReset));
    }

    @Test
    void 個人ランキングは今シーズンでなければ表の上に理由を出し終わったシーズンは月を言語ごとに書く() {
        ViewMapper mapper = new ViewMapper(new LabelResolver(IcuMessageSource.forBasename("messages")),
                mock(CountryNames.class), mock(TimeFormatter.class));
        List<PlayerRankingResponse.RankedPlayer> players = List.of(new PlayerRankingResponse.RankedPlayer(
                "#Y9R22RQ2", "Ian77", 3835, 1, null));
        PlayerRanking finished = new PlayerRanking(players, PlayerRanking.Status.FINISHED_SEASON, YearMonth.of(2026, 9));

        assertNull(mapper.toPlayerRanking(new PlayerRanking(players, PlayerRanking.Status.CURRENT_SEASON, null),
                Locale.JAPANESE).notice());
        assertEquals("今シーズンのランキングにはまだプレイヤーがいないため、前のシーズン(2026年9月)の最終順位を表示しています。",
                mapper.toPlayerRanking(finished, Locale.JAPANESE).notice());
        assertEquals(1, mapper.toPlayerRanking(finished, Locale.JAPANESE).rows().size());
        assertTrue(mapper.toPlayerRanking(finished, Locale.ENGLISH).notice()
                .contains("this season's ranking yet, so the final standings of the previous season (September 2026)"));
        assertTrue(mapper.toPlayerRanking(finished, Locale.forLanguageTag("ko")).notice().contains("지난 시즌(2026년 9월)"));
        assertEquals("今シーズンのランキングには、まだプレイヤーがいません。",
                mapper.toPlayerRanking(PlayerRanking.empty(), Locale.JAPANESE).notice());
        assertEquals("ランキングを取得できませんでした。しばらく時間をおいてからお試しください。",
                mapper.toPlayerRanking(PlayerRanking.unavailable(), Locale.JAPANESE).notice());
    }

    @Test
    void クランの参加条件はタイプが分からなければラベルも空きも出さない() {
        when(labels.message(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        ClanResponse open = new ClanResponse("#XXX", "償い", "", 120000, 0, 47, List.of(), "open", 6000, 800, null);
        ClanResponse unknown = new ClanResponse("#YYY", "謎", "", 120000, 0, 10, List.of(), "secret", 0, 0, null);

        assertEquals(new ClanJoinView("clan.type.open", 6000, null, 800, 3), viewMapper.toClanJoin(open, Locale.JAPANESE));
        assertEquals(new ClanJoinView(null, 0, null, 0, 0), viewMapper.toClanJoin(unknown, Locale.JAPANESE));
    }

    @Test
    void お気に入りクランは見つかった取得できないを区別する() {
        ClanResponse found = new ClanResponse("#XXX", "償い", "", 120000, 0, 40, List.of(), null, 0, 0, null);

        List<FavoriteClanView> rows = viewMapper.toFavoriteClanRows(List.of(
                new FavoriteFetch.Found<>("XXX", "償い", found),
                new FavoriteFetch.Unavailable<>("YYY", "不明")));

        assertTrue(rows.get(0).found());
        assertEquals(120000, rows.get(0).clanScore());
        assertEquals(40, rows.get(0).members());
        assertFalse(rows.get(1).found());
        assertEquals("不明", rows.get(1).name());
    }

    @Test
    void 進化とヒーローで使ったカードはその形の画像とラベルで出す() {
        when(labels.message("card.evolution", Locale.JAPANESE)).thenReturn("限界突破");
        when(labels.message("card.hero", Locale.JAPANESE)).thenReturn("ヒーロー");
        BattleLogEntry.IconUrls icons = new BattleLogEntry.IconUrls("normal.png", "evolution.png", "hero.png");
        List<BattleLogEntry.Card> cards = List.of(
                new BattleLogEntry.Card(1, "Knight", 16, 16, 3, 1, icons),
                new BattleLogEntry.Card(2, "Knight", 16, 16, 3, 2, icons),
                new BattleLogEntry.Card(3, "Knight", 16, 16, 3, null, icons));
        BattleLogEntry battle = duel(List.of(new BattleLogEntry.Participant("#VIEWER", "Viewer", 1, cards, List.of(), null)));

        List<CardView> views = viewMapper.toBattleDetail(battle, "viewer", Locale.JAPANESE).team().get(0).cards();

        assertEquals("evolution.png", views.get(0).iconUrl());
        assertEquals("限界突破", views.get(0).formLabel());
        assertEquals("form-evolution", views.get(0).formClass());
        assertEquals("hero.png", views.get(1).iconUrl());
        assertEquals("ヒーロー", views.get(1).formLabel());
        assertEquals("normal.png", views.get(2).iconUrl());
        assertNull(views.get(2).formLabel());
        assertNull(views.get(2).formClass());
    }

    @Test
    void 形の画像が無いカードは通常の画像で出す() {
        BattleLogEntry.IconUrls icons = new BattleLogEntry.IconUrls("normal.png", null, null);
        BattleLogEntry battle = duel(List.of(new BattleLogEntry.Participant("#VIEWER", "Viewer", 1,
                List.of(new BattleLogEntry.Card(1, "Zap", 16, 16, 2, 1, icons)), List.of(), null)));

        CardView view = viewMapper.toBattleDetail(battle, "viewer", Locale.JAPANESE).team().get(0).cards().get(0);

        assertEquals("normal.png", view.iconUrl());
        assertEquals(CardForm.EVOLUTION, view.form());
    }

    @Test
    void デッキのカードにはエリクサーを付けタワーユニットには付けない() {
        BattleLogEntry battle = duel(List.of(new BattleLogEntry.Participant("#VIEWER", "Viewer", 1,
                List.of(new BattleLogEntry.Card(1, "Knight", 16, 16, 3, null, null),
                        new BattleLogEntry.Card(2, "Mirror", 14, 14, null, null, null)),
                List.of(new BattleLogEntry.Card(159000000, "Tower Princess", 16, 16, null, null, null)), null)));

        ParticipantView viewer = viewMapper.toBattleDetail(battle, "viewer", Locale.JAPANESE).team().get(0);

        assertEquals("3", viewer.cards().get(0).elixir().text());
        // 鏡のようにコストが固定でないカードは "?"。
        assertEquals("?", viewer.cards().get(1).elixir().text());
        assertNull(viewer.supportCards().get(0).elixir());
    }

    @Test
    void トッププレイヤーのデッキは集計した形の画像で出す() {
        CardsResponse.Card knight = new CardsResponse.Card(26000000, "Knight", 16, 3, 3, "common",
                new CardsResponse.Card.IconUrls("normal.png", "evolution.png", "hero.png"));
        TopDecks.SampledDeck deck = new TopDecks.SampledDeck(List.of(26000000, 26000000), null,
                new TopDecks.Player("#A", "Miku", 1, 2887, List.of(16, 16), null,
                        List.of(CardForm.HERO, CardForm.NORMAL)));

        TopPlayerDeckView view = viewMapper.toTopPlayerDeck(deck, Map.of(26000000, knight), Locale.JAPANESE);

        assertEquals("hero.png", view.cards().get(0).iconUrl());
        assertEquals("normal.png", view.cards().get(1).iconUrl());
        assertEquals("3", view.cards().get(0).elixir().text());
    }

    private static BattleLogEntry.Card card(int level, int maxLevel) {
        return new BattleLogEntry.Card(1, "Card", level, maxLevel, 3, null, null);
    }

    private static BattleLogEntry duel(List<BattleLogEntry.Participant> team) {
        return new BattleLogEntry("PvP", "20260101T000000.000Z", new BattleLogEntry.GameMode("TeamVsTeam"),
                team, List.of(participant("#OPP1", "Opp1"), participant("#OPP2", "Opp2")), null);
    }

    private static BattleLogEntry boatBattle(String side) {
        return new BattleLogEntry("boatBattle", "20260101T000000.000Z", new BattleLogEntry.GameMode("ClanWar_BoatBattle"),
                List.of(participant("#VIEWER", "Viewer")), List.of(participant("#OPP1", "Opp1")), side);
    }

    private static BattleLogEntry.Participant participant(String tag, String name) {
        return new BattleLogEntry.Participant(tag, name, 1, List.of(), List.of(), null);
    }
}
