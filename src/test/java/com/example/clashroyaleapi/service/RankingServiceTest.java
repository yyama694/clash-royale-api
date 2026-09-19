package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.ClanRankingResponse;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;
import com.example.clashroyaleapi.client.exception.ApiUnavailableException;
import com.example.clashroyaleapi.store.PlayerSightingLog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

        assertEquals("#P1", rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID, 3).get(0).tag());
        assertEquals("#P2", rankingService.topPlayers("57000122", 3).get(0).tag());
    }

    @Test
    void 個人ランキングは件数に関係なく最大件数で取得し先頭を切り出す() {
        when(apiClient.getPathOfLegendRankings("global", RankingService.MAX_PLAYER_RANKING_SIZE))
                .thenReturn(List.of(rankedPlayer(1, "#P1"), rankedPlayer(2, "#P2"), rankedPlayer(3, "#P3")));

        List<PlayerRankingResponse.RankedPlayer> top2 = rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID, 2);
        List<PlayerRankingResponse.RankedPlayer> all = rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID,
                RankingService.MAX_PLAYER_RANKING_SIZE);

        assertEquals(List.of("#P1", "#P2"), top2.stream().map(PlayerRankingResponse.RankedPlayer::tag).toList());
        assertEquals(3, all.size());
        // 件数違いでも同じ引数で呼ぶので、キャッシュのキーが1つにまとまる。
        verify(apiClient, times(2)).getPathOfLegendRankings("global", RankingService.MAX_PLAYER_RANKING_SIZE);
        verifyNoMoreInteractions(apiClient);
    }

    @Test
    void 個人ランキングの件数が最大件数を超えると例外() {
        assertThrows(IllegalArgumentException.class, () -> rankingService.topPlayers(
                RankingService.GLOBAL_LOCATION_ID, RankingService.MAX_PLAYER_RANKING_SIZE + 1));
    }

    @Test
    void 個人ランキングもAPI障害時は空リストを返す() {
        when(apiClient.getPathOfLegendRankings(anyString(), anyInt()))
                .thenThrow(new ApiUnavailableException("boom", null));

        assertTrue(rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID, 3).isEmpty());
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
        return new ClanResponse("#TAG", "clan", "", 140000, warTrophies, 50, List.of());
    }

    private static PlayerRankingResponse.RankedPlayer rankedPlayer(int rank, String tag) {
        return new PlayerRankingResponse.RankedPlayer(tag, "player" + rank, 70, 4000 - rank, rank,
                new PlayerRankingResponse.Clan("#C1", "clan"));
    }

    private static ClanRankingResponse.RankedClan rankedClan(int rank, String tag) {
        return new ClanRankingResponse.RankedClan(tag, "clan" + rank, rank, 50000, 50,
                new ClanRankingResponse.Location("Japan", "JP"));
    }
}
