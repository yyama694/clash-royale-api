package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.client.exception.ApiUnavailableException;
import com.example.clashroyaleapi.domain.CurrentDeck;
import com.example.clashroyaleapi.domain.ProPlayer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProPlayerServiceTest {

    private static final Instant START = Instant.parse("2026-10-24T06:00:00Z");

    private PlayerService playerService;
    private MutableClock clock;
    private List<Runnable> refreshes;
    private ProPlayerService proPlayerService;

    @BeforeEach
    void setUp() {
        playerService = mock(PlayerService.class);
        clock = new MutableClock(START);
        refreshes = new ArrayList<>();
        proPlayerService = service(pro("#AAA", "Unranked"), pro("#BBB", "Second"), pro("#CCC", "Down"),
                pro("#DDD", "First"), pro("#EEE", "Unranked2"));
        when(playerService.currentDeckOf(any(), anyList())).thenReturn(Optional.empty());
    }

    @Test
    void 世界順位の順に並べ_順位の無い人はトロフィーの多い順_取得できなかった人は最後() {
        when(playerService.findPlayer("#AAA")).thenReturn(player("#AAA", 9000, null));
        when(playerService.findPlayer("#BBB")).thenReturn(player("#BBB", 14000, 20));
        when(playerService.findPlayer("#CCC")).thenThrow(new ApiUnavailableException("boom", null));
        when(playerService.findPlayer("#DDD")).thenReturn(player("#DDD", 14000, 3));
        when(playerService.findPlayer("#EEE")).thenReturn(player("#EEE", 12000, null));

        List<ProPlayerService.Entry> entries = proPlayerService.current();

        assertEquals(List.of("First", "Second", "Unranked2", "Unranked", "Down"),
                entries.stream().map(entry -> entry.pro().name()).toList());
        assertEquals(3, entries.getFirst().rank());
        assertNull(entries.get(2).rank());
        assertFalse(entries.getLast().available());
    }

    @Test
    void 対戦履歴が取れなくてもプロフィールからデッキを出す() {
        proPlayerService = service(pro("#AAA", "Light"));
        PlayerResponse player = player("#AAA", 14000, 5);
        CurrentDeck deck = new CurrentDeck(List.of(), List.of(), false);
        when(playerService.findPlayer("#AAA")).thenReturn(player);
        when(playerService.findBattleLog("#AAA")).thenThrow(new ApiUnavailableException("boom", null));
        when(playerService.currentDeckOf(eq(player), eq(List.<BattleLogEntry>of()))).thenReturn(Optional.of(deck));

        ProPlayerService.Entry entry = proPlayerService.current().getFirst();

        assertTrue(entry.available());
        assertSame(deck, entry.deck());
    }

    @Test
    void 結果が古くなったら前の結果を返しつつ裏で取り直す() {
        proPlayerService = service(pro("#AAA", "Light"));
        when(playerService.findPlayer("#AAA")).thenReturn(player("#AAA", 14000, 5), player("#AAA", 14000, 2));
        assertEquals(5, proPlayerService.current().getFirst().rank());

        clock.advance(Duration.ofMinutes(1));
        assertEquals(5, proPlayerService.current().getFirst().rank());
        assertTrue(refreshes.isEmpty());

        clock.advance(Duration.ofMinutes(1));
        assertEquals(5, proPlayerService.current().getFirst().rank());
        assertEquals(5, proPlayerService.current().getFirst().rank());
        assertEquals(1, refreshes.size());

        refreshes.getFirst().run();
        assertEquals(2, proPlayerService.current().getFirst().rank());
        verify(playerService, times(2)).findPlayer("#AAA");
    }

    @Test
    void 取り直しで取得できなかった人は前の結果を使う() {
        proPlayerService = service(pro("#AAA", "Light"));
        when(playerService.findPlayer("#AAA")).thenReturn(player("#AAA", 14000, 5))
                .thenThrow(new ApiUnavailableException("boom", null));
        proPlayerService.current();

        clock.advance(Duration.ofMinutes(2));
        proPlayerService.current();
        refreshes.getFirst().run();

        ProPlayerService.Entry entry = proPlayerService.current().getFirst();
        assertTrue(entry.available());
        assertEquals(5, entry.rank());
    }

    @Test
    void タグの書き方が違っても一覧の人を見つける() {
        assertEquals("First", proPlayerService.find("ddd").orElseThrow().name());
        assertTrue(proPlayerService.find("#ZZZ").isEmpty());
    }

    private ProPlayerService service(ProPlayer... pros) {
        return new ProPlayerService(List.of(pros), playerService, clock, refreshes::add);
    }

    private static ProPlayer pro(String tag, String name) {
        return new ProPlayer(tag, name, ProPlayer.Kind.PRO, "JP", null, null);
    }

    /** @param rank ランク戦の世界順位。無ければ null(レーティング0の人と同じ扱い) */
    private static PlayerResponse player(String tag, int trophies, Integer rank) {
        PlayerResponse.RankedSeasonResult ranked = rank == null
                ? new PlayerResponse.RankedSeasonResult(null, 0, null)
                : new PlayerResponse.RankedSeasonResult(7, 3000 - rank, rank);
        return new PlayerResponse(tag, tag, 16, trophies, trophies, 0, 0, 0, null, List.of(), List.of(), ranked,
                null, List.of());
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
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
