package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.client.dto.ClanRankingResponse;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.domain.CardCollection;
import com.example.clashroyaleapi.domain.FavoriteFetch;
import com.example.clashroyaleapi.service.CardService;
import com.example.clashroyaleapi.web.view.BattleDetailView;
import com.example.clashroyaleapi.web.view.CardCatalogGroupView;
import com.example.clashroyaleapi.web.view.CardCollectionView;
import com.example.clashroyaleapi.web.view.ClanRankingRowView;
import com.example.clashroyaleapi.web.view.BattleSummaryView;
import com.example.clashroyaleapi.web.view.FavoriteClanView;
import com.example.clashroyaleapi.web.view.FavoritePlayerView;
import com.example.clashroyaleapi.web.view.OpponentView;
import com.example.clashroyaleapi.web.view.ParticipantView;
import com.example.clashroyaleapi.web.view.PlayerLinkView;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    private ViewMapper viewMapper;

    @BeforeEach
    void setUp() {
        LabelResolver labels = mock(LabelResolver.class);
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
                List.of(participant("#VIEWER", "Viewer")), List.of(participant("#OPP1", "<c6>Ale :D")));

        BattleSummaryView summary = viewMapper.toBattleSummaries(List.of(battle), "#VIEWER", Locale.JAPANESE).get(0);

        assertEquals("Ale :D", summary.opponents().get(0).name());
        assertTrue(summary.teammates().isEmpty());
    }

    /** APIのlevelはレアリティごとに数え直した値なので、ゲーム内表記に直してから平均する。 */
    @Test
    void 対戦相手の平均レベルはゲーム内表記で求める() {
        List<BattleLogEntry.Card> deck = List.of(
                card(14, 16), card(14, 16), card(14, 16), card(14, 16),
                card(12, 14), card(12, 14), card(9, 11), card(5, 8));
        BattleLogEntry battle = new BattleLogEntry("PvP", "20260101T000000.000Z",
                new BattleLogEntry.GameMode("Ladder"), List.of(participant("#VIEWER", "Viewer")),
                List.of(new BattleLogEntry.Participant("#OPP1", "Opp1", 0, deck, List.of())));

        BattleSummaryView summary = viewMapper.toBattleSummaries(List.of(battle), "#VIEWER", Locale.JAPANESE).get(0);

        // ゲーム内表記ではコモン14・レア14・エピック14・レジェンダリー13。生値の平均(11.75)にならないこと。
        assertEquals(13.875, summary.opponents().get(0).averageLevel());
    }

    @Test
    void 八枚そろっていないデッキの相手は平均レベルを出さない() {
        BattleLogEntry battle = new BattleLogEntry("PvP", "20260101T000000.000Z",
                new BattleLogEntry.GameMode("Ladder"),
                List.of(participant("#VIEWER", "Viewer")), List.of(participant("#OPP1", "Opp1")));

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
                List.of(new BattleLogEntry.Participant("#OPP1", "Opp1", 0, deck, List.of()),
                        new BattleLogEntry.Participant("#OPP2", "Opp2", 0, deck, List.of())));

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
    void お気に入りプレイヤーは見つかった順位無し見つからない取得できないを区別する() {
        PlayerResponse.RankedSeasonResult ranked = new PlayerResponse.RankedSeasonResult(1, 4500, 12);
        PlayerResponse found = new PlayerResponse("#AAA", "太郎", 10, 5000, 5000, 1, 0, 0,
                new PlayerResponse.ClanRef("#CLAN", "償い"), List.of(), List.of(), null, ranked, null, List.of());
        PlayerResponse noRank = new PlayerResponse("#BBB", "次郎", 5, 1000, 1000, 0, 0, 0, null, List.of(), List.of(),
                null, new PlayerResponse.RankedSeasonResult(null, 0, null), null, List.of());

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
    void お気に入りクランは見つかった取得できないを区別する() {
        ClanResponse found = new ClanResponse("#XXX", "償い", "", 120000, 0, 40, List.of());

        List<FavoriteClanView> rows = viewMapper.toFavoriteClanRows(List.of(
                new FavoriteFetch.Found<>("XXX", "償い", found),
                new FavoriteFetch.Unavailable<>("YYY", "不明")));

        assertTrue(rows.get(0).found());
        assertEquals(120000, rows.get(0).clanScore());
        assertEquals(40, rows.get(0).members());
        assertFalse(rows.get(1).found());
        assertEquals("不明", rows.get(1).name());
    }

    private static BattleLogEntry.Card card(int level, int maxLevel) {
        return new BattleLogEntry.Card(1, "Card", level, maxLevel, 3, null);
    }

    private static BattleLogEntry duel(List<BattleLogEntry.Participant> team) {
        return new BattleLogEntry("PvP", "20260101T000000.000Z", new BattleLogEntry.GameMode("TeamVsTeam"),
                team, List.of(participant("#OPP1", "Opp1"), participant("#OPP2", "Opp2")));
    }

    private static BattleLogEntry.Participant participant(String tag, String name) {
        return new BattleLogEntry.Participant(tag, name, 1, List.of(), List.of());
    }
}
