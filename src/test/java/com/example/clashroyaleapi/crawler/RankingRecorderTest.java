package com.example.clashroyaleapi.crawler;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;
import com.example.clashroyaleapi.client.exception.ApiRateLimitException;
import com.example.clashroyaleapi.domain.RankingSnapshot;
import com.example.clashroyaleapi.service.RankingHistoryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingRecorderTest {

    private ClashRoyaleApiClient apiClient;
    private RankingHistoryService historyService;
    private RankingRecorder recorder;

    @BeforeEach
    void setUp() {
        apiClient = mock(ClashRoyaleApiClient.class);
        historyService = mock(RankingHistoryService.class);
        recorder = new RankingRecorder(apiClient, historyService,
                Clock.fixed(Instant.parse("2026-10-06T09:16:00.123Z"), ZoneOffset.UTC));
    }

    @Test
    void 世界ランキングをシーズン付きで秒に切りそろえた時刻で記録する() throws IOException {
        when(apiClient.getPathOfLegendRankings("global", 1000)).thenReturn(List.of(
                new PlayerRankingResponse.RankedPlayer("#ABC", "<c1>Miku</c>", 3001, 1, null)));
        when(apiClient.getLatestFinishedSeasonId()).thenReturn("2026-09");

        recorder.record();

        ArgumentCaptor<RankingSnapshot> captor = ArgumentCaptor.forClass(RankingSnapshot.class);
        verify(historyService).record(captor.capture());
        assertEquals(new RankingSnapshot(Instant.parse("2026-10-06T09:16:00Z"), "2026-09",
                List.of(new RankingSnapshot.Entry(1, "#ABC", "Miku", 3001))), captor.getValue());
    }

    @Test
    void ランキングが空なら記録しない() throws IOException {
        when(apiClient.getPathOfLegendRankings("global", 1000)).thenReturn(List.of());

        recorder.record();

        verify(historyService, never()).record(any());
    }

    @Test
    void シーズンが取れなければ記録しない() throws IOException {
        when(apiClient.getPathOfLegendRankings("global", 1000)).thenReturn(List.of(
                new PlayerRankingResponse.RankedPlayer("#ABC", "Miku", 3001, 1, null)));
        when(apiClient.getLatestFinishedSeasonId()).thenThrow(new ApiRateLimitException("slow down", null));

        recorder.record();

        verify(historyService, never()).record(any());
    }
}
