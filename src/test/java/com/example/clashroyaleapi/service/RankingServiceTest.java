package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.ClanRankingResponse;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;
import com.example.clashroyaleapi.client.exception.ApiUnavailableException;
import com.example.clashroyaleapi.store.PlayerSightingLog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RankingServiceTest {

    private ClashRoyaleApiClient apiClient;
    private RankingService rankingService;

    @BeforeEach
    void setUp() {
        apiClient = mock(ClashRoyaleApiClient.class);
        rankingService = new RankingService(apiClient, mock(PlayerSightingLog.class));
    }

    @Test
    void 指定したlocationIdのランキングを返す() {
        when(apiClient.getClanRankings(eq("global"), anyInt())).thenReturn(List.of(rankedClan(1, "#AAA")));
        when(apiClient.getClanRankings(eq("57000122"), anyInt())).thenReturn(List.of(rankedClan(1, "#BBB")));

        assertEquals("#AAA", rankingService.topClans(RankingService.GLOBAL_LOCATION_ID).get(0).tag());
        assertEquals("#BBB", rankingService.topClans("57000122").get(0).tag());
    }

    @Test
    void API障害時は例外を投げずに空リストを返す() {
        when(apiClient.getClanRankings(anyString(), anyInt()))
                .thenThrow(new ApiUnavailableException("boom", null));

        assertTrue(rankingService.topClans(RankingService.GLOBAL_LOCATION_ID).isEmpty());
    }


    @Test
    void 個人ランキングも指定したlocationIdで取得する() {
        when(apiClient.getPathOfLegendRankings(eq("global"), anyInt())).thenReturn(List.of(rankedPlayer(1, "#P1")));
        when(apiClient.getPathOfLegendRankings(eq("57000122"), anyInt())).thenReturn(List.of(rankedPlayer(1, "#P2")));

        assertEquals("#P1", rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID, 3).players().get(0).tag());
        assertEquals("#P2", rankingService.topPlayers("57000122", 3).players().get(0).tag());
    }

    @Test
    void 個人ランキングは件数に関係なく最大件数で取得し先頭を切り出す() {
        when(apiClient.getPathOfLegendRankings("global", RankingService.MAX_PLAYER_RANKING_SIZE))
                .thenReturn(List.of(rankedPlayer(1, "#P1"), rankedPlayer(2, "#P2"), rankedPlayer(3, "#P3")));

        PlayerRanking top2 = rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID, 2);
        PlayerRanking all = rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID,
                RankingService.MAX_PLAYER_RANKING_SIZE);

        assertEquals(List.of("#P1", "#P2"), top2.players().stream().map(PlayerRankingResponse.RankedPlayer::tag).toList());
        assertEquals(PlayerRanking.Status.CURRENT_SEASON, top2.status());
        assertEquals(3, all.players().size());
        // 件数違いでも同じ引数で呼ぶので、キャッシュのキーが1つにまとまる。
        verify(apiClient, times(2)).getPathOfLegendRankings("global", RankingService.MAX_PLAYER_RANKING_SIZE);
        verifyNoMoreInteractions(apiClient);
    }

    @Test
    void 世界のランキングが上限に届かないうちは新しいシーズンが始まったばかりと伝える() {
        when(apiClient.getPathOfLegendRankings("global", RankingService.MAX_PLAYER_RANKING_SIZE))
                .thenReturn(List.of(rankedPlayer(1, "#P1"), rankedPlayer(2, "#P2")));
        when(apiClient.getPathOfLegendRankings("57000122", RankingService.MAX_PLAYER_RANKING_SIZE))
                .thenReturn(List.of(rankedPlayer(1, "#J1")));

        assertEquals(PlayerRanking.Status.NEW_SEASON, rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID,
                RankingService.MAX_PLAYER_RANKING_SIZE).status());
        assertEquals(PlayerRanking.Status.NEW_SEASON, rankingService.topPlayers("57000122", 100).status());
        // 画面の件数に足りていれば、断る必要はない。
        assertEquals(PlayerRanking.Status.CURRENT_SEASON,
                rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID, 2).status());
    }

    @Test
    void 世界のランキングが上限まであれば国別の人数が少なくても新しいシーズンとは言わない() {
        when(apiClient.getPathOfLegendRankings("global", RankingService.MAX_PLAYER_RANKING_SIZE))
                .thenReturn(IntStream.rangeClosed(1, RankingService.MAX_PLAYER_RANKING_SIZE)
                        .mapToObj(rank -> rankedPlayer(rank, "#P" + rank)).toList());
        when(apiClient.getPathOfLegendRankings("57000122", RankingService.MAX_PLAYER_RANKING_SIZE))
                .thenReturn(List.of(rankedPlayer(1, "#J1")));

        assertEquals(PlayerRanking.Status.CURRENT_SEASON, rankingService.topPlayers("57000122", 100).status());
    }

    @Test
    void 世界のランキングが取れなければ国別は新しいシーズンとは言わない() {
        when(apiClient.getPathOfLegendRankings("global", RankingService.MAX_PLAYER_RANKING_SIZE))
                .thenThrow(new ApiUnavailableException("boom", null));
        when(apiClient.getPathOfLegendRankings("57000122", RankingService.MAX_PLAYER_RANKING_SIZE))
                .thenReturn(List.of(rankedPlayer(1, "#J1")));

        assertEquals(PlayerRanking.Status.CURRENT_SEASON, rankingService.topPlayers("57000122", 100).status());
    }

    @Test
    void 個人ランキングの件数が最大件数を超えると例外() {
        assertThrows(IllegalArgumentException.class, () -> rankingService.topPlayers(
                RankingService.GLOBAL_LOCATION_ID, RankingService.MAX_PLAYER_RANKING_SIZE + 1));
    }

    @Test
    void 個人ランキングもAPI障害時は空にして取得できなかったことを伝える() {
        when(apiClient.getPathOfLegendRankings(anyString(), anyInt()))
                .thenThrow(new ApiUnavailableException("boom", null));

        PlayerRanking ranking = rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID, 3);

        assertTrue(ranking.players().isEmpty());
        assertEquals(PlayerRanking.Status.UNAVAILABLE, ranking.status());
        verify(apiClient, never()).getLatestFinishedSeasonId();
    }

    @Test
    void 今シーズンのグローバルランキングが空なら終わったシーズンの最終順位を返す() {
        when(apiClient.getPathOfLegendRankings("global", RankingService.MAX_PLAYER_RANKING_SIZE)).thenReturn(List.of());
        when(apiClient.getLatestFinishedSeasonId()).thenReturn("2026-09");
        when(apiClient.getFinishedSeasonPathOfLegendRankings("2026-09", RankingService.MAX_PLAYER_RANKING_SIZE))
                .thenReturn(List.of(rankedPlayer(1, "#P1"), rankedPlayer(2, "#P2"), rankedPlayer(3, "#P3")));

        PlayerRanking ranking = rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID, 2);

        assertEquals(PlayerRanking.Status.FINISHED_SEASON, ranking.status());
        assertEquals(YearMonth.of(2026, 9), ranking.finishedSeason());
        assertEquals(List.of("#P1", "#P2"), ranking.players().stream().map(PlayerRankingResponse.RankedPlayer::tag).toList());
    }

    @Test
    void 今シーズンの国別ランキングが空なら終わったシーズンを取りに行かずまだ誰もいないと伝える() {
        // 国別の終わったシーズンの順位は公式APIが返さない(404)。
        when(apiClient.getPathOfLegendRankings("57000122", RankingService.MAX_PLAYER_RANKING_SIZE)).thenReturn(List.of());

        PlayerRanking ranking = rankingService.topPlayers("57000122", 3);

        assertEquals(PlayerRanking.Status.EMPTY, ranking.status());
        assertTrue(ranking.players().isEmpty());
        verify(apiClient, never()).getLatestFinishedSeasonId();
    }

    @Test
    void 終わったシーズンも取れなければ今シーズンにまだ誰もいないことだけを伝える() {
        when(apiClient.getPathOfLegendRankings("global", RankingService.MAX_PLAYER_RANKING_SIZE)).thenReturn(List.of());
        when(apiClient.getLatestFinishedSeasonId()).thenReturn("2026-09");
        when(apiClient.getFinishedSeasonPathOfLegendRankings(anyString(), anyInt()))
                .thenThrow(new ApiUnavailableException("boom", null));

        assertEquals(PlayerRanking.Status.EMPTY, rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID, 3).status());
    }

    @Test
    void 終わったシーズンの一覧が空か想定外の形でも今シーズンにまだ誰もいないことだけを伝える() {
        when(apiClient.getPathOfLegendRankings("global", RankingService.MAX_PLAYER_RANKING_SIZE)).thenReturn(List.of());
        when(apiClient.getLatestFinishedSeasonId()).thenReturn(null, "2026-9x");
        when(apiClient.getFinishedSeasonPathOfLegendRankings(anyString(), anyInt()))
                .thenReturn(List.of(rankedPlayer(1, "#P1")));

        assertEquals(PlayerRanking.Status.EMPTY, rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID, 3).status());
        assertEquals(PlayerRanking.Status.EMPTY, rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID, 3).status());
    }

    @Test
    void クラン対戦トロフィーは上位クランだけタグをキーに取得する() {
        List<ClanRankingResponse.RankedClan> clans = IntStream.rangeClosed(1, RankingService.WAR_TROPHIES_RANK_LIMIT + 1)
                .mapToObj(rank -> rankedClan(rank, "#C" + rank))
                .toList();
        for (ClanRankingResponse.RankedClan clan : clans) {
            when(apiClient.getClan(clan.tag())).thenReturn(clanDetail(clan.rank() * 100));
        }

        Map<String, Integer> warTrophies = rankingService.warTrophiesOfTopClans("global", clans);

        assertEquals(RankingService.WAR_TROPHIES_RANK_LIMIT, warTrophies.size());
        assertEquals(100, warTrophies.get("#C1"));
        assertFalse(warTrophies.containsKey("#C" + (RankingService.WAR_TROPHIES_RANK_LIMIT + 1)));
    }

    @Test
    void クラン対戦トロフィーは個別のクランで失敗しても他のクランに影響しない() {
        when(apiClient.getClan("#OK")).thenReturn(clanDetail(500));
        when(apiClient.getClan("#NG")).thenThrow(new ApiUnavailableException("boom", null));

        Map<String, Integer> warTrophies = rankingService
                .warTrophiesOfTopClans("global", List.of(rankedClan(1, "#OK"), rankedClan(2, "#NG")));

        assertEquals(500, warTrophies.get("#OK"));
        assertFalse(warTrophies.containsKey("#NG"));
    }

    private static ClanResponse clanDetail(int warTrophies) {
        return new ClanResponse("#TAG", "clan", "", 140000, warTrophies, 50, List.of(), null, 0, 0, null);
    }

    private static PlayerRankingResponse.RankedPlayer rankedPlayer(int rank, String tag) {
        return new PlayerRankingResponse.RankedPlayer(tag, "player" + rank, 4000 - rank, rank,
                new PlayerRankingResponse.Clan("#C1", "clan"));
    }

    private static ClanRankingResponse.RankedClan rankedClan(int rank, String tag) {
        return new ClanRankingResponse.RankedClan(tag, "clan" + rank, rank, 50000, 50,
                new ClanRankingResponse.Location("Japan", "JP"));
    }
}
