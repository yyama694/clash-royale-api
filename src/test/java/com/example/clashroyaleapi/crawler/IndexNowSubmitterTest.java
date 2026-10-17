package com.example.clashroyaleapi.crawler;

import com.example.clashroyaleapi.client.IndexNowClient;
import com.example.clashroyaleapi.client.exception.ApiUnavailableException;
import com.example.clashroyaleapi.config.IndexNowProperties;
import com.example.clashroyaleapi.service.RankingHistoryService;
import com.example.clashroyaleapi.service.RankingService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndexNowSubmitterTest {

    private static final String SITE = "https://example.test";
    private static final String KEY = "0123456789abcdef";

    @TempDir
    Path dir;

    private IndexNowClient client;
    private RankingHistoryService historyService;
    private RankingService rankingService;
    private IndexNowSubmitter submitter;

    @BeforeEach
    void setUp() {
        client = mock(IndexNowClient.class);
        historyService = mock(RankingHistoryService.class);
        rankingService = mock(RankingService.class);
        submitter = new IndexNowSubmitter(client, historyService, rankingService,
                new IndexNowProperties(true, KEY, SITE, "0 30 0 * * *"), dir.resolve("indexnow/submitted.txt"));
    }

    @Test
    void 日ごとの動きとシーズンのページを言語の無いURLと各言語のURLで送る() {
        when(historyService.archiveDays()).thenReturn(List.of(LocalDate.of(2026, 10, 16)));
        when(rankingService.finishedSeasons()).thenReturn(List.of(YearMonth.of(2026, 9)));

        List<String> urls = submitter.urls();

        assertEquals(22, urls.size());
        assertTrue(urls.contains(SITE + "/ranking/players/movements/2026-10-16"));
        assertTrue(urls.contains(SITE + "/ranking/players/movements/2026-10-16?lang=ja"));
        assertTrue(urls.contains(SITE + "/ranking/players/seasons/2026-09?lang=ko"));
    }

    @Test
    void 送ったURLは二度送らず_新しい日の分だけを送る() {
        when(rankingService.finishedSeasons()).thenReturn(List.of());
        when(historyService.archiveDays()).thenReturn(List.of(LocalDate.of(2026, 10, 16)));
        List<String> firstDay = submitter.urls();
        submitter.submit();
        verify(client).submit("example.test", KEY, SITE + "/" + KEY + ".txt", firstDay);

        when(historyService.archiveDays()).thenReturn(List.of(LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 16)));
        List<String> secondDay = submitter.urls().stream().filter(url -> url.contains("2026-10-17")).toList();
        submitter.submit();

        assertEquals(11, secondDay.size());
        verify(client).submit("example.test", KEY, SITE + "/" + KEY + ".txt", secondDay);
    }

    @Test
    void 送れなかったときは記録せず次の回に送り直す() {
        when(rankingService.finishedSeasons()).thenReturn(List.of());
        when(historyService.archiveDays()).thenReturn(List.of(LocalDate.of(2026, 10, 16)));
        List<String> urls = submitter.urls();
        doThrow(new ApiUnavailableException("down", null)).when(client).submit(any(), any(), any(), anyList());
        submitter.submit();

        IndexNowClient recovered = mock(IndexNowClient.class);
        new IndexNowSubmitter(recovered, historyService, rankingService,
                new IndexNowProperties(true, KEY, SITE, "0 30 0 * * *"), dir.resolve("indexnow/submitted.txt")).submit();

        verify(recovered).submit("example.test", KEY, SITE + "/" + KEY + ".txt", urls);
    }

    @Test
    void 新しいページが無ければ送らない() {
        when(rankingService.finishedSeasons()).thenReturn(List.of());
        when(historyService.archiveDays()).thenReturn(List.of());

        submitter.submit();

        verify(client, never()).submit(any(), any(), any(), anyList());
    }
}
