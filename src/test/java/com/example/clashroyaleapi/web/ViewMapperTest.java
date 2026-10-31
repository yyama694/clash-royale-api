package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.client.dto.ClanRankingResponse;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.config.IcuMessageSource;
import com.example.clashroyaleapi.domain.BattleResult;
import com.example.clashroyaleapi.domain.CardCollection;
import com.example.clashroyaleapi.domain.CardForm;
import com.example.clashroyaleapi.domain.CardStats;
import com.example.clashroyaleapi.domain.CardUsage;
import com.example.clashroyaleapi.domain.ClanWarParticipation;
import com.example.clashroyaleapi.domain.DailyQuiz;
import com.example.clashroyaleapi.domain.DeckWinRates;
import com.example.clashroyaleapi.domain.FavoriteFetch;
import com.example.clashroyaleapi.domain.QuizQuestion;
import com.example.clashroyaleapi.domain.RankingSnapshot;
import com.example.clashroyaleapi.domain.SeasonRankLog;
import com.example.clashroyaleapi.domain.SimilarDecks;
import com.example.clashroyaleapi.domain.TopDecks;
import com.example.clashroyaleapi.service.CardImageService;
import com.example.clashroyaleapi.service.CardService;
import com.example.clashroyaleapi.service.PlayerRanking;
import com.example.clashroyaleapi.web.view.RankedLeagueView;
import com.example.clashroyaleapi.web.view.BattleDetailView;
import com.example.clashroyaleapi.web.view.CardCatalogGroupView;
import com.example.clashroyaleapi.web.view.CardCatalogItemView;
import com.example.clashroyaleapi.web.view.ClanWarView;
import com.example.clashroyaleapi.web.view.CardCollectionView;
import com.example.clashroyaleapi.web.view.CardDetailView;
import com.example.clashroyaleapi.web.view.CardOptionView;
import com.example.clashroyaleapi.web.view.CardStatsView;
import com.example.clashroyaleapi.web.view.CardView;
import com.example.clashroyaleapi.web.view.ClanJoinView;
import com.example.clashroyaleapi.web.view.ClanRankingRowView;
import com.example.clashroyaleapi.web.view.BattleSummaryView;
import com.example.clashroyaleapi.web.view.DeckFilterRowView;
import com.example.clashroyaleapi.web.view.FavoriteClanView;
import com.example.clashroyaleapi.web.view.FavoritePlayerView;
import com.example.clashroyaleapi.web.view.OpponentView;
import com.example.clashroyaleapi.web.view.ParticipantView;
import com.example.clashroyaleapi.web.view.PlayerLinkView;
import com.example.clashroyaleapi.web.view.PlayerProgressView;
import com.example.clashroyaleapi.web.view.PlayerRankingView;
import com.example.clashroyaleapi.web.view.QuizView;
import com.example.clashroyaleapi.web.view.RankChangeView;
import com.example.clashroyaleapi.web.view.TopPlayerDeckView;
import com.example.clashroyaleapi.web.view.TrophyChangeView;
import com.example.clashroyaleapi.web.view.WinRateDeckView;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 2v2で「自分」と「味方」を取り違えない並べ方を中心に検証する。ラベル解決はモックで素通しにする。 */
class ViewMapperTest {

    private LabelResolver labels;
    private CardImageService cardImages;
    private ViewMapper viewMapper;

    @BeforeEach
    void setUp() {
        labels = mock(LabelResolver.class);
        when(labels.gameMode(anyString(), anyString(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(labels.cardName(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(labels.rarity(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        cardImages = mock(CardImageService.class);
        when(cardImages.usable(any())).thenAnswer(invocation -> invocation.getArgument(0));
        viewMapper = new ViewMapper(labels, mock(CountryNames.class), mock(TimeFormatter.class), cardImages);
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
    void クラン対戦のデュエルは取ったラウンドの数をスコアにし_平均レベルは全ラウンドのデッキで求める() {
        List<BattleLogEntry.Card> level16 = List.of(card(16, 16), card(16, 16), card(16, 16), card(16, 16),
                card(16, 16), card(16, 16), card(16, 16), card(16, 16));
        List<BattleLogEntry.Card> level14 = List.of(card(12, 14), card(12, 14), card(12, 14), card(12, 14),
                card(12, 14), card(12, 14), card(12, 14), card(12, 14));
        BattleLogEntry battle = clanWarDuel(
                duelist("#VIEWER", List.of(new BattleLogEntry.Round(3, level16, 1.5),
                        new BattleLogEntry.Round(1, level16, 2.5), new BattleLogEntry.Round(1, level16, 3.5))),
                duelist("#OPP1", List.of(new BattleLogEntry.Round(0, level16, 4.0),
                        new BattleLogEntry.Round(2, level14, 5.0), new BattleLogEntry.Round(2, level14, 6.0))));

        BattleSummaryView summary = viewMapper.toBattleSummaries(List.of(battle), "#VIEWER", Locale.JAPANESE).get(0);

        // クラウンの合計は5-4だが、ラウンドは1勝2敗で負け。
        assertEquals(BattleResult.LOSE, summary.result());
        assertEquals(1, summary.selfScore());
        assertEquals(2, summary.opponentScore());
        assertTrue(summary.roundScore());
        // 24枚をまとめると8枚そろったデッキにならないが、ラウンドごとのデッキで求める。(16+14+14)/3
        assertEquals(44.0 / 3, summary.opponents().get(0).averageLevel(), 1e-9);
    }

    @Test
    void クラン対戦のデュエルの対戦詳細はラウンドごとにそれぞれの側から見たスコアと消費漏れを出す() {
        List<BattleLogEntry.Card> deck = List.of(card(16, 16), card(16, 16), card(16, 16), card(16, 16),
                card(16, 16), card(16, 16), card(16, 16), card(16, 16));
        BattleLogEntry battle = clanWarDuel(
                duelist("#VIEWER", List.of(new BattleLogEntry.Round(1, deck, 1.5), new BattleLogEntry.Round(0, deck, 2.5))),
                duelist("#OPP1", List.of(new BattleLogEntry.Round(3, deck, 4.0), new BattleLogEntry.Round(1, deck, 5.0))));

        BattleDetailView detail = viewMapper.toBattleDetail(battle, "#VIEWER", Locale.JAPANESE);

        ParticipantView viewer = detail.team().get(0);
        assertEquals(0, viewer.score());
        assertTrue(viewer.roundScore());
        // 公式APIの elixirLeaked は最後のラウンドの値なので、対戦全体としては出さない。
        assertNull(viewer.elixirLeaked());
        assertEquals(2, viewer.rounds().size());
        assertEquals(1, viewer.rounds().get(0).number());
        assertEquals(1, viewer.rounds().get(0).crowns());
        assertEquals(3, viewer.rounds().get(0).opponentCrowns());
        assertEquals(BattleResult.LOSE, viewer.rounds().get(0).result());
        assertEquals(1.5, viewer.rounds().get(0).elixirLeaked());
        assertEquals(16.0, viewer.rounds().get(0).meta().averageLevel());
        ParticipantView opponent = detail.opponents().get(0);
        assertEquals(2, opponent.score());
        assertEquals(BattleResult.WIN, opponent.rounds().get(1).result());
        assertEquals(1, opponent.rounds().get(1).crowns());
        assertEquals(0, opponent.rounds().get(1).opponentCrowns());
    }

    @Test
    void ラウンドの無い対戦の対戦詳細はクラウン数をスコアにする() {
        BattleLogEntry battle = new BattleLogEntry("PvP", "20260101T000000.000Z",
                new BattleLogEntry.GameMode("Ladder"), List.of(participant("#VIEWER", "Viewer")),
                List.of(new BattleLogEntry.Participant("#OPP1", "Opp1", 2, List.of(), List.of(), null, 3.0)), null);

        BattleDetailView detail = viewMapper.toBattleDetail(battle, "#VIEWER", Locale.JAPANESE);

        assertEquals(1, detail.team().get(0).score());
        assertFalse(detail.team().get(0).roundScore());
        assertTrue(detail.team().get(0).rounds().isEmpty());
        assertEquals(3.0, detail.opponents().get(0).elixirLeaked());
    }

    @Test
    void 対戦履歴の自分のデッキは見ているプレイヤー本人のもので味方のものではない() {
        List<BattleLogEntry.Card> viewerDeck = List.of(card(14, 16), card(14, 16), card(14, 16), card(14, 16),
                card(12, 14), card(12, 14), card(9, 11), card(5, 8));
        BattleLogEntry battle = duel(List.of(
                new BattleLogEntry.Participant("#MATE", "Mate", 1, List.of(card(14, 16)), List.of(), null),
                new BattleLogEntry.Participant("#VIEWER", "Viewer", 1, viewerDeck, List.of(), null)));

        BattleSummaryView summary = viewMapper.toBattleSummaries(List.of(battle), "#VIEWER", Locale.JAPANESE).get(0);

        assertEquals(8, summary.selfCards().size());
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
    void カードの絞り込みは選んだ枚数分の欄と空の欄を出し8枚で空の欄を出さない() {
        List<DeckFilterRowView> none = viewMapper.toDeckFilterRows(List.of(), 8);
        assertEquals(List.of(new DeckFilterRowView(null, List.of(), Set.of(), false)), none);

        List<DeckFilterRowView> two = viewMapper.toDeckFilterRows(List.of(5, 7), 8);
        assertEquals(List.of(
                new DeckFilterRowView(5, List.of(7), Set.of(7), false),
                new DeckFilterRowView(7, List.of(5), Set.of(5), false),
                new DeckFilterRowView(null, List.of(5, 7), Set.of(5, 7), true)), two);

        List<DeckFilterRowView> full = viewMapper.toDeckFilterRows(List.of(1, 2, 3, 4, 5, 6, 7, 8), 8);
        assertEquals(8, full.size());
        assertTrue(full.stream().allMatch(row -> row.cardId() != null));
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

        Instant now = Instant.parse("2026-10-15T00:00:00Z");
        assertEquals(new PlayerProgressView(14000, 900, 400, 25, 3100, "2026-10"), viewMapper.toPlayerProgress(ranked, now));
        assertEquals(new PlayerProgressView(1000, 30, 20, null, null, "2026-10"), viewMapper.toPlayerProgress(noRank, now));
        assertEquals(new PlayerProgressView(0, 0, 0, null, null, "2026-10"), viewMapper.toPlayerProgress(noResult, now));
        assertEquals(new PlayerProgressView(14000, 900, 400, null, null, "2026-10"),
                viewMapper.toPlayerProgress(justReset, now));
    }

    @Test
    void バランス調整の値は言語ごとの書き方にし_60秒以上は分と秒に分ける() {
        assertEquals("1,210", ViewMapper.balanceValue("1210", labels, Locale.ENGLISH));
        assertEquals("5,5", ViewMapper.balanceValue("5.5", labels, Locale.GERMAN));
        assertEquals("1.5秒", ViewMapper.balanceValue("1.5s", labels, Locale.JAPANESE));
        assertEquals("1分33秒", ViewMapper.balanceValue("93s", labels, Locale.JAPANESE));
        assertEquals("0.93s", ViewMapper.balanceValue("0.93s", labels, Locale.ENGLISH));
    }

    @Test
    void バランス調整のマスは秒と取り違えず言語ごとの単位で出す() {
        // 単位の語と複数形は messages にあるので、本番と同じ ICU のメッセージソースで組み立てる。
        LabelResolver icu = new LabelResolver(IcuMessageSource.forBasename("messages"));
        assertEquals("0.5マス", ViewMapper.balanceValue("0.5tiles", icu, Locale.JAPANESE));
        assertEquals("0.4 tiles", ViewMapper.balanceValue("0.4tiles", icu, Locale.ENGLISH));
        assertEquals("1 tile", ViewMapper.balanceValue("1tiles", icu, Locale.ENGLISH));
        assertEquals("0,5 Felder", ViewMapper.balanceValue("0.5tiles", icu, Locale.GERMAN));
        assertEquals("0,5 клетки", ViewMapper.balanceValue("0.5tiles", icu, Locale.forLanguageTag("ru")));
    }

    @Test
    void 前回からの変化のシーズンは切り替えの時刻で次の月になる() {
        PlayerResponse player = new PlayerResponse("#AAA", "太郎", 10, 14000, 14000, 900, 400, 100, null,
                List.of(), List.of(), new PlayerResponse.RankedSeasonResult(7, 3100, 25), null, List.of());

        assertEquals("2026-10", viewMapper.toPlayerProgress(player, Instant.parse("2026-11-02T08:59:59Z")).season());
        assertEquals("2026-11", viewMapper.toPlayerProgress(player, Instant.parse("2026-11-02T09:00:00Z")).season());
    }

    @Test
    void 個人ランキングは今シーズンでなければ表の上に理由を出し終わったシーズンは月を言語ごとに書く() {
        ViewMapper mapper = new ViewMapper(new LabelResolver(IcuMessageSource.forBasename("messages")),
                mock(CountryNames.class), mock(TimeFormatter.class), mock(CardImageService.class));
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
        PlayerRanking newSeason = PlayerRanking.newSeason(players);
        assertEquals("新しいシーズンが始まったばかりのため、今シーズンのランキングに入っているのはまだ1人です。",
                mapper.toPlayerRanking(newSeason, Locale.JAPANESE).notice());
        assertEquals("The new season has just started, so only 1 player is in this season's ranking so far.",
                mapper.toPlayerRanking(newSeason, Locale.ENGLISH).notice());
        assertTrue(mapper.toPlayerRanking(newSeason, Locale.forLanguageTag("ru")).notice().contains("только 1 игрок."));
        assertEquals("今シーズンのランキングには、まだプレイヤーがいません。",
                mapper.toPlayerRanking(PlayerRanking.empty(), Locale.JAPANESE).notice());
        assertEquals("ランキングを取得できませんでした。しばらく時間をおいてからお試しください。",
                mapper.toPlayerRanking(PlayerRanking.unavailable(), Locale.JAPANESE).notice());
    }

    @Test
    void 世界の今シーズンの順位には24時間前の記録との差を付け_終わったシーズンには付けない() {
        ViewMapper mapper = new ViewMapper(new LabelResolver(IcuMessageSource.forBasename("messages")),
                mock(CountryNames.class), mock(TimeFormatter.class), mock(CardImageService.class));
        List<PlayerRankingResponse.RankedPlayer> players = List.of(
                new PlayerRankingResponse.RankedPlayer("#A", "A", 3900, 1, null),
                new PlayerRankingResponse.RankedPlayer("#B", "B", 3800, 2, null),
                new PlayerRankingResponse.RankedPlayer("#C", "C", 3700, 3, null),
                new PlayerRankingResponse.RankedPlayer("#D", "D", 3600, 4, null));
        RankingSnapshot dayAgo = new RankingSnapshot(Instant.EPOCH, "2026-09", List.of(
                new RankingSnapshot.Entry(1, "#B", "B", 3850),
                new RankingSnapshot.Entry(2, "#A", "A", 3840),
                new RankingSnapshot.Entry(3, "#C", "C", 3700),
                new RankingSnapshot.Entry(4, "#X", "X", 3650)));

        PlayerRankingView view = mapper.toPlayerRanking(PlayerRanking.current(players), dayAgo, Locale.JAPANESE);

        assertTrue(view.showChange());
        assertEquals(new RankChangeView("▲1", "24時間で1位上がった", "up"), view.rows().get(0).change());
        assertEquals(new RankChangeView("▼1", "24時間で1位下がった", "down"), view.rows().get(1).change());
        assertEquals("same", view.rows().get(2).change().direction());
        assertEquals(new RankChangeView("NEW", "24時間前は1,000位より下", "new"), view.rows().get(3).change());
        assertEquals("Up 1 place in 24 hours",
                mapper.toPlayerRanking(PlayerRanking.current(players), dayAgo, Locale.ENGLISH).rows().get(0)
                        .change().label());

        PlayerRankingView finished = mapper.toPlayerRanking(
                PlayerRanking.finishedSeason(players, YearMonth.of(2026, 9)), dayAgo, Locale.JAPANESE);
        assertFalse(finished.showChange());
        assertNull(finished.rows().get(0).change());
        assertFalse(mapper.toPlayerRanking(PlayerRanking.current(players), Locale.JAPANESE).showChange());
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
    void 置き場所にまだ無い形の画像はデッキでは通常の画像にしカード詳細では出さない() {
        when(cardImages.usable("evolution.png")).thenReturn(null);
        BattleLogEntry.IconUrls icons = new BattleLogEntry.IconUrls("normal.png", "evolution.png", "hero.png");
        BattleLogEntry battle = duel(List.of(new BattleLogEntry.Participant("#VIEWER", "Viewer", 1,
                List.of(new BattleLogEntry.Card(1, "Electro Giant", 16, 16, 7, 1, icons)), List.of(), null)));
        CardsResponse.Card card = new CardsResponse.Card(1, "Electro Giant", 16, 3, 7, "epic",
                new CardsResponse.Card.IconUrls("normal.png", "evolution.png", "hero.png"));

        CardView inDeck = viewMapper.toBattleDetail(battle, "viewer", Locale.JAPANESE).team().get(0).cards().get(0);
        CardDetailView detail = viewMapper.toCardDetail(card, stats(), Locale.JAPANESE);

        assertEquals("normal.png", inDeck.iconUrl());
        assertEquals(CardForm.EVOLUTION, inDeck.form());
        assertNull(detail.evolutionIconUrl());
        assertEquals("hero.png", detail.heroIconUrl());
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

    @Test
    void ランク戦の対戦はモード名にその対戦のリーグを添える() {
        ViewMapper mapper = new ViewMapper(new LabelResolver(IcuMessageSource.forBasename("messages")),
                mock(CountryNames.class), mock(TimeFormatter.class), mock(CardImageService.class));
        BattleLogEntry ranked = new BattleLogEntry("pathOfLegend", "20261014T000000.000Z",
                new BattleLogEntry.GameMode("Ranked1v1_NewArena"), List.of(participant("#VIEWER", "Viewer")),
                List.of(participant("#OPP1", "Opp1")), null, 2);
        BattleLogEntry unknownLeague = new BattleLogEntry("pathOfLegend", "20261014T000000.000Z",
                new BattleLogEntry.GameMode("Ranked1v1_NewArena"), List.of(participant("#VIEWER", "Viewer")),
                List.of(participant("#OPP1", "Opp1")), null, 9);

        List<BattleSummaryView> rows = mapper.toBattleSummaries(List.of(ranked, unknownLeague), "#VIEWER", Locale.JAPANESE);

        assertEquals("ランク戦(マスターII)", rows.get(0).gameMode());
        assertEquals("ランク戦", rows.get(1).gameMode());
        assertTrue(mapper.toBattleSummaries(List.of(new BattleLogEntry("pathOfLegend", "20261014T000000.000Z",
                new BattleLogEntry.GameMode("Ranked1v1_NewArena"), List.of(participant("#VIEWER", "Viewer")),
                List.of(participant("#OPP1", "Opp1")), null, 7)), "#VIEWER", Locale.ENGLISH)
                .getFirst().gameMode().endsWith(" (Ultimate Champion)"));
        assertEquals("フレンドバトル", mapper.toBattleSummaries(List.of(new BattleLogEntry("friendly",
                "20261014T000000.000Z", new BattleLogEntry.GameMode("Friendly"),
                List.of(participant("#VIEWER", "Viewer")), List.of(participant("#OPP1", "Opp1")), null, 1)),
                "#VIEWER", Locale.JAPANESE).getFirst().gameMode());
    }

    @Test
    void 世界の順位の無い人には今シーズンのリーグを出し_天界ならレーティングも添える() {
        ViewMapper mapper = new ViewMapper(new LabelResolver(IcuMessageSource.forBasename("messages")),
                mock(CountryNames.class), mock(TimeFormatter.class), mock(CardImageService.class));

        assertEquals(new RankedLeagueView("マスターIII", null), mapper.toCurrentRankedLeague(
                rankedPlayer(14000, new PlayerResponse.RankedSeasonResult(3, 0, null)), Locale.JAPANESE));
        assertEquals(new RankedLeagueView("天界", 1650), mapper.toCurrentRankedLeague(
                rankedPlayer(14000, new PlayerResponse.RankedSeasonResult(7, 1650, null)), Locale.JAPANESE));
        assertEquals(new RankedLeagueView("マスターI", null), mapper.toCurrentRankedLeague(
                rankedPlayer(14000, new PlayerResponse.RankedSeasonResult(1, 0, null)), Locale.JAPANESE));
        assertNull(mapper.toCurrentRankedLeague(
                rankedPlayer(9000, new PlayerResponse.RankedSeasonResult(1, 0, null)), Locale.JAPANESE));
        assertNull(mapper.toCurrentRankedLeague(
                rankedPlayer(14000, new PlayerResponse.RankedSeasonResult(7, 3100, 25)), Locale.JAPANESE));
        assertNull(mapper.toCurrentRankedLeague(rankedPlayer(14000, null), Locale.JAPANESE));
    }

    private static PlayerResponse rankedPlayer(int trophies, PlayerResponse.RankedSeasonResult current) {
        return new PlayerResponse("#AAA", "太郎", 10, trophies, trophies, 900, 400, 100, null,
                List.of(), List.of(), current, null, List.of());
    }

    private static BattleLogEntry.Card card(int level, int maxLevel) {
        return new BattleLogEntry.Card(1, "Card", level, maxLevel, 3, null, null);
    }

    private static BattleLogEntry duel(List<BattleLogEntry.Participant> team) {
        return new BattleLogEntry("PvP", "20260101T000000.000Z", new BattleLogEntry.GameMode("TeamVsTeam"),
                team, List.of(participant("#OPP1", "Opp1"), participant("#OPP2", "Opp2")), null);
    }

    private static BattleLogEntry clanWarDuel(BattleLogEntry.Participant viewer, BattleLogEntry.Participant opponent) {
        return new BattleLogEntry("riverRaceDuel", "20260101T000000.000Z", new BattleLogEntry.GameMode("CW_Duel_1v1"),
                List.of(viewer), List.of(opponent), null);
    }

    /** 公式APIと同じく、crowns は全ラウンドの合計、cards は全ラウンドのデッキをつなげたもの、elixirLeaked は最後のラウンドの値。 */
    private static BattleLogEntry.Participant duelist(String tag, List<BattleLogEntry.Round> rounds) {
        return new BattleLogEntry.Participant(tag, tag, rounds.stream().mapToInt(BattleLogEntry.Round::crowns).sum(),
                rounds.stream().flatMap(round -> round.cards().stream()).toList(), List.of(), null,
                rounds.get(rounds.size() - 1).elixirLeaked(), rounds);
    }

    private static BattleLogEntry boatBattle(String side) {
        return new BattleLogEntry("boatBattle", "20260101T000000.000Z", new BattleLogEntry.GameMode("ClanWar_BoatBattle"),
                List.of(participant("#VIEWER", "Viewer")), List.of(participant("#OPP1", "Opp1")), side);
    }

    private static BattleLogEntry.Participant participant(String tag, String name) {
        return new BattleLogEntry.Participant(tag, name, 1, List.of(), List.of(), null);
    }

    @Test
    void 順位の推移の縦軸は目盛りが4本以内になる間隔で上端と下端をそろえる() {
        assertEquals(List.of(1, 1000, 500), Arrays.stream(ViewMapper.rankAxis(3, 950)).boxed().toList());
        assertEquals(List.of(120, 180, 20), Arrays.stream(ViewMapper.rankAxis(120, 180)).boxed().toList());
        assertEquals(List.of(1, 5, 1), Arrays.stream(ViewMapper.rankAxis(1, 5)).boxed().toList());
        assertEquals(List.of(7, 8, 1), Arrays.stream(ViewMapper.rankAxis(7, 7)).boxed().toList());
    }

    @Test
    void 今シーズンの最高順位がランキングに1000人そろう前のものなら当時の人数を添える() {
        ViewMapper mapper = new ViewMapper(new LabelResolver(IcuMessageSource.forBasename("messages")),
                mock(CountryNames.class), mock(TimeFormatter.class), mock(CardImageService.class));
        SeasonRankLog.Point early = new SeasonRankLog.Point(Instant.parse("2026-10-07T12:46:00Z"), 75, 1749, 133);
        SeasonRankLog.Point later = new SeasonRankLog.Point(Instant.parse("2026-10-16T13:16:00Z"), 223, 2338, 1000);
        SeasonRankLog.Point best = new SeasonRankLog.Point(Instant.parse("2026-10-16T14:16:00Z"), 60, 2500, 1000);

        assertEquals("今シーズンの最高は75位(10/7・当時133人中)",
                mapper.toRankHistory(List.of(early, later), Locale.JAPANESE).best());
        assertEquals("Best this season: #75 (10/7, when only 133 players were ranked)",
                mapper.toRankHistory(List.of(early, later), Locale.ENGLISH).best());
        assertEquals("今シーズンの最高は60位(10/16)",
                mapper.toRankHistory(List.of(early, later, best), Locale.JAPANESE).best());
    }

    @Test
    void 同じデッキの世界ランカーは8枚とも同じでなければ違うカードの名前を添える() {
        ViewMapper mapper = new ViewMapper(new LabelResolver(IcuMessageSource.forBasename("messages")),
                mock(CountryNames.class), mock(TimeFormatter.class), mock(CardImageService.class));
        List<Integer> deck = List.of(1, 2, 3, 4, 5, 6, 7, 8);
        Map<Integer, CardsResponse.Card> cards = Map.of(1, new CardsResponse.Card(1, "Knight", 16, null, 3,
                "common", null));

        assertEquals("世界のトッププレイヤー950人のうち12人が、8枚とも同じデッキを使っています。",
                mapper.toSimilarDecks(new SimilarDecks(deck, 12, 950, false), deck, cards, Locale.JAPANESE).text());
        assertEquals("Of 950 top players worldwide, 1 uses the same 7 cards (all except ⁨Knight⁩).",
                mapper.toSimilarDecks(new SimilarDecks(deck.subList(1, 8), 1, 950, true), deck, cards,
                        Locale.ENGLISH).text());
    }

    @Test
    void 今週まだ攻撃していないメンバーは表と分けて名前だけ並べる() {
        ClanWarParticipation war = new ClanWarParticipation(true, List.of(
                new ClanWarParticipation.Member("#AAA", "A", 0, 0),
                new ClanWarParticipation.Member("#BBB", "B", 0, 4),
                new ClanWarParticipation.Member("#CCC", "C", 4, 8)));

        ClanWarView view = viewMapper.toClanWar(war);

        assertEquals(List.of("B", "C"), view.members().stream().map(ClanWarView.MemberView::name).toList());
        assertEquals(List.of("A"), view.notAttackedThisWeek().stream().map(ClanWarView.MemberView::name).toList());
        assertEquals(3, view.memberCount());
    }

    @Test
    void 使用率ランキングは約1週間前の集計からの増減を表示どおりの値の差で出す() {
        ViewMapper mapper = new ViewMapper(new LabelResolver(IcuMessageSource.forBasename("messages")),
                mock(CountryNames.class), mock(TimeFormatter.class), mock(CardImageService.class));
        List<CardService.CardGroup> groups = List.of(new CardService.CardGroup("common", List.of(
                new CardsResponse.Card(1, "Knight", 14, null, 3, "common", null),
                new CardsResponse.Card(2, "Archers", 14, null, 3, "common", null),
                new CardsResponse.Card(3, "Giant", 14, null, 5, "rare", null))));
        // 今: Knight 2/3=66.7%・Archers 1/3=33.3%・Giant 1/3=33.3%。1週間前: Knight 1/2=50.0%・Archers 1/2=50.0%・Giant 1/2=50.0%
        CardUsage now = CardUsage.of(decks("2026-10-18T19:15:00Z", List.of(1, 2), List.of(1, 3), List.of(9)));
        CardUsage weekAgo = CardUsage.of(decks("2026-10-11T12:18:18Z", List.of(1, 2), List.of(3)));

        List<CardCatalogItemView> cards = mapper.toCardUsageRanking(groups, now, weekAgo, Locale.JAPANESE).get(0).cards();

        assertEquals("▲16.7", cards.get(0).usageChange().text());
        assertEquals("1週間前より16.7ポイント増", cards.get(0).usageChange().label());
        assertEquals("up", cards.get(0).usageChange().direction());
        assertEquals("▼16.7", cards.get(1).usageChange().text());
        assertEquals("down", cards.get(1).usageChange().direction());
        assertEquals("▲16,7", mapper.toCardUsageRanking(groups, now, weekAgo, Locale.GERMAN).get(0).cards().get(0)
                .usageChange().text());
        assertEquals("▲16.7", mapper.toCardUsageRanking(groups, now, weekAgo, Locale.ENGLISH).get(0).cards().get(0)
                .usageChange().text());
        assertNull(mapper.toCardUsageRanking(groups, now, null, Locale.JAPANESE).get(0).cards().get(0).usageChange());
        assertEquals("▲▼は、約1週間前(10月11日)の集計と比べた使用率の増減(ポイント)です。",
                mapper.usageChangeNotice(weekAgo, Locale.JAPANESE));
    }

    @Test
    void 使用率が変わらなければ増減なしとして出す() {
        ViewMapper mapper = new ViewMapper(new LabelResolver(IcuMessageSource.forBasename("messages")),
                mock(CountryNames.class), mock(TimeFormatter.class), mock(CardImageService.class));
        List<CardService.CardGroup> groups = List.of(new CardService.CardGroup("common", List.of(
                new CardsResponse.Card(1, "Knight", 14, null, 3, "common", null))));
        CardUsage now = CardUsage.of(decks("2026-10-18T19:15:00Z", List.of(1), List.of(2)));
        CardUsage weekAgo = CardUsage.of(decks("2026-10-11T12:18:18Z", List.of(1), List.of(2), List.of(1), List.of(3)));

        CardCatalogItemView knight = mapper.toCardUsageRanking(groups, now, weekAgo, Locale.JAPANESE).get(0).cards().get(0);

        assertEquals("±0", knight.usageChange().text());
        assertEquals("1週間前と同じ", knight.usageChange().label());
        assertEquals("same", knight.usageChange().direction());
    }

    @SafeVarargs
    private static TopDecks decks(String collectedAt, List<Integer>... cardIds) {
        return new TopDecks(Instant.parse(collectedAt), Arrays.stream(cardIds)
                .map(ids -> new TopDecks.SampledDeck(ids, null, new TopDecks.Player("#ABC", "Miku", 1, 2887,
                        ids.stream().map(id -> 16).toList(), 16)))
                .toList(), null);
    }

    @Test
    void 勝率のデッキは表示する勝率が50パーセント未満の最初の1件の前にだけ区切りを出す() {
        List<DeckWinRates.DeckRecord> decks = List.of(winRateDeck(60, 100), winRateDeck(199, 400),
                winRateDeck(49, 100), winRateDeck(40, 100));

        List<WinRateDeckView> views = viewMapper.toWinRateDecks(decks, Map.of(), Locale.JAPANESE);

        assertEquals(List.of(60, 50, 49, 40), views.stream().map(WinRateDeckView::winRatePercent).toList());
        assertEquals(List.of(false, false, true, false), views.stream().map(WinRateDeckView::firstBelowHalf).toList());
    }

    @Test
    void カードのステータスの秒と言葉と小数点は表示言語に合わせる() {
        // 秒と言葉はゲームの表記を messages に写してあるので、本番と同じ ICU のメッセージソースで組み立てる。
        ViewMapper mapper = new ViewMapper(new LabelResolver(IcuMessageSource.forBasename("messages")),
                mock(CountryNames.class), mock(TimeFormatter.class), mock(CardImageService.class));

        assertEquals("1.8秒", mapper.statValue("{s:1.8}", Locale.JAPANESE));
        assertEquals("1.8sec", mapper.statValue("{s:1.8}", Locale.ENGLISH));
        assertEquals("1,8s", mapper.statValue("{s:1.8}", Locale.GERMAN));
        assertEquals("4秒-22秒", mapper.statValue("{s:4}-{s:22}", Locale.JAPANESE));
        assertEquals("1.1秒 x2", mapper.statValue("{s:1.1} x2", Locale.JAPANESE));
        // ゲームの表記どおり全角の「／」
        assertEquals("空中／地上", mapper.statValue("{t:stat.TID_TARGETS_AIR_AND_GROUND}", Locale.JAPANESE));
        assertEquals("Air & Ground", mapper.statValue("{t:stat.TID_TARGETS_AIR_AND_GROUND}", Locale.ENGLISH));
        assertEquals("6,5", mapper.statValue("6.5", Locale.GERMAN));
        assertEquals("3,5-6", mapper.statValue("3.5-6", Locale.FRENCH));
        // ゲームの画面どおり、大きな数に桁区切りは入れない
        assertEquals("5743", mapper.statValue("5743", Locale.GERMAN));
        assertEquals("102-490", mapper.statValue("102-490", Locale.ENGLISH));
        assertNull(mapper.statValue(null, Locale.JAPANESE));
    }

    @Test
    void カードのステータスは通常_スキル_限界突破_ヒーローの順に分け載っていないカードは欄ごと出さない() {
        ViewMapper mapper = new ViewMapper(new LabelResolver(IcuMessageSource.forBasename("messages")),
                mock(CountryNames.class), mock(TimeFormatter.class), mock(CardImageService.class));
        List<CardStats.Row> rows = List.of(
                new CardStats.Row(CardStats.Form.HERO, "stat.TID_SPELL_ATTRIBUTE_DAMAGE_PER_SECOND", "408 x2", null, null),
                new CardStats.Row(CardStats.Form.NORMAL, "stat.TID_SPELL_ATTRIBUTE_HEALTH", "1153", "721", null),
                new CardStats.Row(CardStats.Form.HERO, "=砲台のヒットポイント", "2454", "1536", "ja"),
                new CardStats.Row(CardStats.Form.EVOLUTION, "stat.TID_SNIPER_AMMO", "3", "3", null));

        CardStatsView view = mapper.toCardStats(stats("Musketeer", rows), "Musketeer", Locale.JAPANESE);

        assertEquals(3, view.sections().size());
        assertNull(view.sections().get(0).heading());
        assertEquals(new CardStatsView.Row("ヒットポイント", "1153", "721"), view.sections().get(0).rows().get(0));
        assertEquals("限界突破", view.sections().get(1).heading());
        assertEquals("スキル(ヒーロー)", view.sections().get(2).heading());
        assertNull(view.sections().get(2).rows().get(0).level11());
        // 公式の訳が無い項目は日本語の画面にだけ、日本語の画面の表記のまま出す
        assertEquals(new CardStatsView.Row("砲台のヒットポイント", "2454", "1536"), view.sections().get(2).rows().get(1));
        assertEquals(1, mapper.toCardStats(stats("Musketeer", rows), "Musketeer", Locale.ENGLISH)
                .sections().get(2).rows().size());
        assertTrue(view.notice().startsWith("2026年10月25日時点のゲーム内の値"), view.notice());
        assertTrue(view.notice().contains("レベル11(チャレンジレベル)"), view.notice());
        assertNull(mapper.toCardStats(stats("Musketeer", rows), "Tower Princess", Locale.JAPANESE));
    }

    @Test
    void 地上の形は別のカードの名前を見出しにして通常の次に並べ地上のエリクサーはステータスに出さない() {
        ViewMapper mapper = new ViewMapper(new LabelResolver(IcuMessageSource.forBasename("messages")),
                mock(CountryNames.class), mock(TimeFormatter.class), mock(CardImageService.class));
        CardStats stats = new CardStats(LocalDate.of(2026, 10, 25), Map.of(
                "Spirit Empress", List.of(
                        new CardStats.Row(CardStats.Form.NORMAL, "stat.TID_SPELL_ATTRIBUTE_HEALTH", "1791", "1121", null),
                        new CardStats.Row(CardStats.Form.NORMAL, CardStats.GROUND_ELIXIR, "3", "3", null)),
                "Spirit Empress (Ground)", List.of(
                        new CardStats.Row(CardStats.Form.NORMAL, "stat.TID_SPELL_ATTRIBUTE_HIT_SPEED", "{s:1.2}", "{s:1.2}",
                                null))));

        CardStatsView view = mapper.toCardStats(stats, "Spirit Empress", Locale.JAPANESE);

        assertEquals(2, view.sections().size());
        assertEquals(List.of(new CardStatsView.Row("ヒットポイント", "1791", "1121")), view.sections().get(0).rows());
        assertEquals("スピリットエンプレス（地上）", view.sections().get(1).heading());
        assertEquals(List.of(new CardStatsView.Row("攻撃速度", "1.2秒", "1.2秒")), view.sections().get(1).rows());
        assertEquals("Spirit Empress (Ground)",
                mapper.toCardStats(stats, "Spirit Empress", Locale.ENGLISH).sections().get(1).heading());
    }

    @Test
    void 地上の形が無いカードでは地上の形の名前を引かない() {
        CardStats stats = stats("Knight",
                List.of(new CardStats.Row(CardStats.Form.NORMAL, "stat.TID_SPELL_ATTRIBUTE_HEALTH", "2822", "1766", null)));

        viewMapper.toCardStats(stats, "Knight", Locale.JAPANESE);

        // 引くと、訳が無いという警告がカードの数だけログに出る
        verify(labels, never()).cardName(eq("Knight (Ground)"), any());
    }

    @Test
    void 地上に出すと別のコストになるカードはカード詳細に地上の形のコストと画像を出す() {
        when(cardImages.groundIconUrl("Spirit Empress")).thenReturn("/images/cards/spirit-empress-ground.png");
        CardsResponse.Card empress = new CardsResponse.Card(28000025, "Spirit Empress", 8, 3, 6, "legendary",
                new CardsResponse.Card.IconUrls("empress.png", null, null));
        CardsResponse.Card knight = new CardsResponse.Card(26000000, "Knight", 16, 1, 3, "common",
                new CardsResponse.Card.IconUrls("knight.png", null, null));
        CardStats stats = stats("Spirit Empress",
                List.of(new CardStats.Row(CardStats.Form.NORMAL, CardStats.GROUND_ELIXIR, "3", "3", null)));

        CardDetailView detail = viewMapper.toCardDetail(empress, stats, Locale.JAPANESE);

        assertEquals(6, detail.elixirCost());
        assertEquals(new CardDetailView.Ground("Spirit Empress (Ground)", "/images/cards/spirit-empress-ground.png", 3),
                detail.ground());
        assertTrue(detail.hasOtherForms());
        assertNull(viewMapper.toCardDetail(knight, stats, Locale.JAPANESE).ground());
    }

    private static CardStats stats() {
        return new CardStats(LocalDate.of(2026, 10, 25), Map.of());
    }

    private static CardStats stats(String card, List<CardStats.Row> rows) {
        return new CardStats(LocalDate.of(2026, 10, 25), Map.of(card, rows));
    }

    @Test
    void タワーへのダメージには減る割合を表示言語の書き方で添えその説明を出す() {
        ViewMapper mapper = new ViewMapper(new LabelResolver(IcuMessageSource.forBasename("messages")),
                mock(CountryNames.class), mock(TimeFormatter.class), mock(CardImageService.class));
        List<CardStats.Row> fireball = List.of(
                new CardStats.Row(CardStats.Form.NORMAL, "stat.TID_SPELL_ATTRIBUTE_AREA_DAMAGE", "1100", "688", null),
                new CardStats.Row(CardStats.Form.NORMAL, "stat.TID_SPELL_ATTRIBUTE_TOWER_DAMAGE", "253", null, null));

        CardStatsView ja = mapper.toCardStats(stats("Fireball", fireball), "Fireball", Locale.JAPANESE);
        assertEquals(new CardStatsView.Row("タワーへのダメージ", "253(-77%)", null), ja.sections().get(0).rows().get(1));
        assertEquals("1100", ja.sections().get(0).rows().get(0).level16());
        assertTrue(ja.towerReductionNotice().startsWith("「タワーへのダメージ」の横の%は"), ja.towerReductionNotice());
        // トルコ語は％を数字の前に書く
        assertEquals("253 (-%77)", mapper.toCardStats(stats("Fireball", fireball), "Fireball", Locale.forLanguageTag("tr"))
                .sections().get(0).rows().get(1).level16());

        List<CardStats.Row> knight = List.of(
                new CardStats.Row(CardStats.Form.NORMAL, "stat.TID_SPELL_ATTRIBUTE_DAMAGE", "323", "202", null));
        assertNull(mapper.toCardStats(stats("Knight", knight), "Knight", Locale.JAPANESE).towerReductionNotice());
    }

    private static DeckWinRates.DeckRecord winRateDeck(int wins, int battles) {
        return new DeckWinRates.DeckRecord(List.of(26000000), List.of(), null,
                new DeckWinRates.TopPlayer("#ABC", "Miku", 1), battles, wins, 10, 10);
    }

    @Test
    void クイズは通常の姿の画像を使い選択肢を文字にする() {
        CardsResponse.Card knight = new CardsResponse.Card(26000000, "Knight", 16, 1, 3, "common",
                new CardsResponse.Card.IconUrls("knight.png", "knight-evolution.png", "knight-hero.png"));
        DailyQuiz quiz = new DailyQuiz(LocalDate.of(2026, 10, 26), 1,
                List.of(new QuizQuestion.Elixir(26000000, false, List.of(1, 2, 3, 4), 2)));

        QuizView view = viewMapper.toQuiz(quiz, Map.of(26000000, knight), stats(), Locale.JAPANESE);

        assertEquals("2026-10-26", view.date());
        QuizView.Question question = view.questions().get(0);
        assertEquals("26000000", question.key());
        assertEquals(List.of(new QuizView.Card(26000000, "Knight", "knight.png", null, null)), question.cards());
        assertEquals("elixir", question.type());
        assertEquals("1,2,3,4", question.choicesText());
        assertEquals("2", question.answer());
        assertNull(question.note());
        // 30問に満たない日は、選べる問題数もあるだけにする。
        assertEquals(List.of(), view.sizes());
    }

    @Test
    void クイズの地上の形は地上の画像と名前で出しどちらの形かを問題文で言いもう一方のコストを添える() {
        ViewMapper mapper = new ViewMapper(new LabelResolver(IcuMessageSource.forBasename("messages")),
                mock(CountryNames.class), mock(TimeFormatter.class), cardImages);
        when(cardImages.groundIconUrl("Spirit Empress")).thenReturn("/images/cards/spirit-empress-ground.png");
        CardsResponse.Card empress = new CardsResponse.Card(28000025, "Spirit Empress", 8, 3, 6, "legendary",
                new CardsResponse.Card.IconUrls("empress.png", null, null));
        CardsResponse.Card knight = new CardsResponse.Card(26000000, "Knight", 16, 1, 3, "common",
                new CardsResponse.Card.IconUrls("knight.png", null, null));
        CardStats stats = stats("Spirit Empress",
                List.of(new CardStats.Row(CardStats.Form.NORMAL, CardStats.GROUND_ELIXIR, "3", "3", null)));
        DailyQuiz quiz = new DailyQuiz(LocalDate.of(2026, 10, 26), 1, List.of(
                new QuizQuestion.Elixir(28000025, true, List.of(2, 3, 4, 5), 1),
                new QuizQuestion.Elixir(28000025, false, List.of(3, 4, 5, 6), 3),
                new QuizQuestion.Elixir(26000000, false, List.of(1, 2, 3, 4), 2)));

        List<QuizView.Question> questions = mapper.toQuiz(quiz, Map.of(28000025, empress, 26000000, knight), stats,
                Locale.JAPANESE).questions();

        QuizView.Question ground = questions.get(0);
        assertEquals("28000025g", ground.key());
        assertEquals("スピリットエンプレス（地上）", ground.cards().get(0).name());
        assertEquals("/images/cards/spirit-empress-ground.png", ground.cards().get(0).iconUrl());
        assertEquals("地上に出すときのエリクサーは?", ground.prompt());
        assertEquals("霊龍にまたがって出すと6エリクサーです", ground.note());
        QuizView.Question mounted = questions.get(1);
        assertEquals("28000025", mounted.key());
        assertEquals("スピリットエンプレス", mounted.cards().get(0).name());
        assertEquals("empress.png", mounted.cards().get(0).iconUrl());
        assertEquals("霊龍にまたがって出すときのエリクサーは?", mounted.prompt());
        assertEquals("地上に出すと3エリクサーです", mounted.note());
        QuizView.Question other = questions.get(2);
        assertEquals("このカードのエリクサーは?", other.prompt());
        assertNull(other.note());
    }

    @Test
    void クイズの並べ替えは最初に見せる並びのカードとヒットポイントと正しい並びを出し問題の種類の説明を種類の順に並べる() {
        ViewMapper mapper = new ViewMapper(new LabelResolver(IcuMessageSource.forBasename("messages")),
                mock(CountryNames.class), mock(TimeFormatter.class), cardImages);
        Map<Integer, CardsResponse.Card> cards = Map.of(
                26000000, new CardsResponse.Card(26000000, "Knight", 16, 1, 3, "common",
                        new CardsResponse.Card.IconUrls("knight.png", null, null)),
                26000003, new CardsResponse.Card(26000003, "Giant", 16, 1, 5, "rare",
                        new CardsResponse.Card.IconUrls("giant.png", null, null)),
                26000014, new CardsResponse.Card(26000014, "Musketeer", 16, 1, 4, "rare",
                        new CardsResponse.Card.IconUrls("musketeer.png", null, null)),
                26000021, new CardsResponse.Card(26000021, "Hog Rider", 16, 1, 4, "rare",
                        new CardsResponse.Card.IconUrls("hog.png", null, null)));
        DailyQuiz quiz = new DailyQuiz(LocalDate.of(2026, 10, 31), 5, List.of(
                new QuizQuestion.HpOrder(List.of(new QuizQuestion.HpCard(26000014, 1153),
                        new QuizQuestion.HpCard(26000003, 6339), new QuizQuestion.HpCard(26000000, 2822),
                        new QuizQuestion.HpCard(26000021, 2711))),
                new QuizQuestion.Elixir(26000000, false, List.of(1, 2, 3, 4), 2)));

        QuizView view = mapper.toQuiz(quiz, cards, stats(), Locale.JAPANESE);

        // 並べ替えが先に出る日でも、説明はエリクサー → 並べ替えの順
        assertEquals(List.of("カードのエリクサーコストを当てよう", "ヒットポイントの多い順に並べよう"), view.leads());
        QuizView.Question question = view.questions().get(0);
        assertEquals("hp", question.type());
        assertEquals("hp:26000014-26000003-26000000-26000021", question.key());
        assertEquals("ヒットポイントが多い順に並べよう", question.prompt());
        assertEquals("ヒットポイントはレベル16の値です", question.note());
        assertEquals(List.of(new QuizView.Card(26000014, "マスケット銃士", "musketeer.png", "1153", null),
                new QuizView.Card(26000003, "ジャイアント", "giant.png", "6339", null),
                new QuizView.Card(26000000, "ナイト", "knight.png", "2822", null),
                new QuizView.Card(26000021, "ホグライダー", "hog.png", "2711", null)), question.cards());
        assertEquals("1230", question.answer());
        assertEquals("", question.choicesText());
    }

    @Test
    void クイズの並べ替えはどの姿のヒットポイントかの注釈を添える() {
        ViewMapper mapper = new ViewMapper(new LabelResolver(IcuMessageSource.forBasename("messages")),
                mock(CountryNames.class), mock(TimeFormatter.class), cardImages);
        Map<Integer, CardsResponse.Card> cards = Map.of(
                1, new CardsResponse.Card(1, "Suspicious Bush", 16, null, 2, "rare", null),
                2, new CardsResponse.Card(2, "Barbarians", 16, null, 5, "common", null),
                3, new CardsResponse.Card(3, "Dark Prince", 16, null, 4, "epic", null),
                4, new CardsResponse.Card(4, "Guards", 16, null, 3, "epic", null),
                5, new CardsResponse.Card(5, "Knight", 16, null, 3, "common", null));
        CardStats stats = new CardStats(LocalDate.of(2026, 10, 25), Map.of(
                "Barbarians", List.of(new CardStats.Row(CardStats.Form.NORMAL, CardStats.COUNT, "5", "5", null)),
                "Dark Prince", List.of(new CardStats.Row(CardStats.Form.NORMAL, CardStats.SHIELD_HEALTH, "384", "240", null)),
                "Guards", List.of(new CardStats.Row(CardStats.Form.NORMAL, CardStats.COUNT, "3", "3", null),
                        new CardStats.Row(CardStats.Form.NORMAL, CardStats.SHIELD_HEALTH, "409", "256", null))));
        DailyQuiz quiz = new DailyQuiz(LocalDate.of(2026, 10, 31), 5, List.of(
                new QuizQuestion.HpOrder(List.of(new QuizQuestion.HpCard(1, 130), new QuizQuestion.HpCard(2, 1145),
                        new QuizQuestion.HpCard(3, 1918), new QuizQuestion.HpCard(4, 130))),
                new QuizQuestion.HpOrder(List.of(new QuizQuestion.HpCard(5, 2822), new QuizQuestion.HpCard(2, 1145),
                        new QuizQuestion.HpCard(3, 1918), new QuizQuestion.HpCard(1, 130)))));

        List<QuizView.Card> shown = mapper.toQuiz(quiz, cards, stats, Locale.JAPANESE).questions().get(0).cards();

        // 別のユニットが出るカードはカードごとの文言、何体も出る・シールドがあるだけのカードはステータスの行から
        assertEquals("草のヒットポイント(飛び出すステルスゴブリンは含まない)", shown.get(0).condition());
        assertEquals("1体あたり", shown.get(1).condition());
        assertEquals("シールドを除く", shown.get(2).condition());
        assertEquals("1体あたり・シールドを除く", shown.get(3).condition());
        assertNull(mapper.toQuiz(quiz, cards, stats, Locale.JAPANESE).questions().get(1).cards().get(0).condition());
    }
}
