package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.ClanRankingResponse;
import com.example.clashroyaleapi.client.exception.ApiUnavailableException;
import com.example.clashroyaleapi.domain.RankingScope;

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
    void グローバルとローカルで異なるlocationIdを使う() {
        when(apiClient.getClanRankings(eq("global"), anyInt())).thenReturn(List.of(rankedClan(1, "#AAA")));
        when(apiClient.getClanRankings(eq("57000122"), anyInt())).thenReturn(List.of(rankedClan(1, "#BBB")));

        assertEquals("#AAA", rankingService.topClans(RankingScope.GLOBAL).get(0).tag());
        assertEquals("#BBB", rankingService.topClans(RankingScope.LOCAL).get(0).tag());
    }

    @Test
    void API障害時は例外を投げずに空リストを返す() {
        when(apiClient.getClanRankings(anyString(), anyInt()))
                .thenThrow(new ApiUnavailableException("boom", null));

        assertTrue(rankingService.topClans(RankingScope.GLOBAL).isEmpty());
    }

    private static ClanRankingResponse.RankedClan rankedClan(int rank, String tag) {
        return new ClanRankingResponse.RankedClan(tag, "clan" + rank, rank, 50000, 50,
                new ClanRankingResponse.Location("Japan"));
    }
}
