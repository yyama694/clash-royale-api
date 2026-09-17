package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.ClanRankingResponse;
import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;
import com.example.clashroyaleapi.client.exception.ApiUnavailableException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RankingServiceTest {

    private ClashRoyaleApiClient apiClient;
    private RankingService rankingService;

    @BeforeEach
    void setUp() {
        apiClient = mock(ClashRoyaleApiClient.class);
        rankingService = new RankingService(apiClient);
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
    void 個人ランキングもAPI障害時は空リストを返す() {
        when(apiClient.getPathOfLegendRankings(anyString(), anyInt()))
                .thenThrow(new ApiUnavailableException("boom", null));

        assertTrue(rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID, 3).isEmpty());
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
