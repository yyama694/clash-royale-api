package com.example.clashroyaleapi.crawler;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.ClanRankingResponse;
import com.example.clashroyaleapi.client.dto.RiverRaceLogResponse;
import com.example.clashroyaleapi.client.exception.ApiRateLimitException;
import com.example.clashroyaleapi.client.exception.ResourceNotFoundException;
import com.example.clashroyaleapi.config.CrawlerProperties;
import com.example.clashroyaleapi.domain.PlayerSighting;
import com.example.clashroyaleapi.store.PlayerSightingLog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrawlerTest {

    @TempDir
    Path dir;

    private ClashRoyaleApiClient apiClient;
    private PlayerSightingLog sightingLog;
    private TagQueue clanQueue;
    private TagQueue playerQueue;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        apiClient = mock(ClashRoyaleApiClient.class);
        sightingLog = mock(PlayerSightingLog.class);
        clanQueue = new TagQueue(dir.resolve("clans"), 4);
        playerQueue = new TagQueue(dir.resolve("players"), 4);
        clock = new MutableClock(Instant.parse("2026-09-19T00:00:00Z"));
    }

    private Crawler crawler(int battleLogEvery) {
        return new Crawler(apiClient, sightingLog, clanQueue, playerQueue,
                new CrawlerProperties(true, Duration.ofSeconds(2), 1, battleLogEvery), clock);
    }

    @Test
    void 回すクランが無ければグローバルのクランランキングを起点にする() throws IOException {
        when(apiClient.getClanRankings("global", 1000)).thenReturn(List.of(
                new ClanRankingResponse.RankedClan("#AAA", "A", 1, 0, 50, null)));

        crawler(5).crawlNext();

        assertEquals("#AAA", clanQueue.peek().orElseThrow());
    }

    @Test
    void クラン対戦の参加者を記録し対戦相手のクランと各クランの1人を次に回す() throws IOException {
        clanQueue.discover(List.of("#AAA"));
        when(apiClient.getRiverRaceLog("#AAA", 1)).thenReturn(raceLog(
                clan("#AAA", "#P1", "#P2"), clan("#BBB", "#P3")));

        crawler(5).crawlNext();

        verify(sightingLog).recordCrawled(List.of(
                sighting("#P1"), sighting("#P2"), sighting("#P3")));
        assertEquals(List.of("#BBB"), TagQueueTest.take(clanQueue, 1));
        assertEquals(List.of("#P1", "#P3"), TagQueueTest.take(playerQueue, 2));
    }

    @Test
    void 決まった回数に1回はプレイヤーの対戦履歴を回し対戦相手を次に回す() throws IOException {
        playerQueue.discover(List.of("#ME"));
        when(apiClient.getBattleLogUncached("#ME")).thenReturn(List.of(new BattleLogEntry(
                "PvP", "20260919T000000.000Z", null,
                List.of(participant("#ME")), List.of(participant("#RIV")))));

        crawler(1).crawlNext();

        verify(sightingLog).recordCrawled(List.of(sighting("#ME"), sighting("#RIV")));
        verify(apiClient, never()).getRiverRaceLog(anyString(), anyInt());
        assertEquals(List.of("#RIV"), TagQueueTest.take(playerQueue, 1));
    }

    @Test
    void 見つからないクランは飛ばす() throws IOException {
        clanQueue.discover(List.of("#AAA", "#BBB"));
        when(apiClient.getRiverRaceLog(anyString(), anyInt())).thenThrow(new ResourceNotFoundException("gone", null));
        String first = clanQueue.peek().orElseThrow();

        crawler(5).crawlNext();

        assertFalse(clanQueue.peek().orElseThrow().equals(first));
    }

    @Test
    void 呼び出し回数の制限に掛かったら同じクランを後で回し直す() throws IOException {
        clanQueue.discover(List.of("#AAA"));
        when(apiClient.getRiverRaceLog(anyString(), anyInt()))
                .thenThrow(new ApiRateLimitException("slow down", null))
                .thenReturn(raceLog(clan("#AAA", "#P1")));
        Crawler crawler = crawler(5);

        crawler.crawlNext();
        crawler.crawlNext();
        verify(apiClient, times(1)).getRiverRaceLog(anyString(), anyInt());

        clock.advance(Duration.ofMinutes(1));
        crawler.crawlNext();

        verify(apiClient, times(2)).getRiverRaceLog(eq("#AAA"), anyInt());
        verify(sightingLog).recordCrawled(List.of(sighting("#P1")));
    }

    private static PlayerSighting sighting(String tag) {
        return new PlayerSighting(tag, "name");
    }

    private static BattleLogEntry.Participant participant(String tag) {
        return new BattleLogEntry.Participant(tag, "name", 0, List.of(), List.of());
    }

    private static RiverRaceLogResponse raceLog(RiverRaceLogResponse.Clan... clans) {
        List<RiverRaceLogResponse.Standing> standings = List.of(clans).stream()
                .map(RiverRaceLogResponse.Standing::new)
                .toList();
        return new RiverRaceLogResponse(List.of(new RiverRaceLogResponse.RiverRace(standings)));
    }

    private static RiverRaceLogResponse.Clan clan(String tag, String... participantTags) {
        return new RiverRaceLogResponse.Clan(tag, "clan", List.of(participantTags).stream()
                .map(p -> new RiverRaceLogResponse.Participant(p, "name"))
                .toList());
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
