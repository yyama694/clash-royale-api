package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.domain.TopBattle;
import com.example.clashroyaleapi.store.TopBattleStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopBattleServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");

    @TempDir
    Path dir;

    private static TopBattle battle(String time, String tag, int crowns) {
        return new TopBattle(Instant.parse(time), new TopBattle.Side(tag, List.of(1), crowns, 2.0),
                new TopBattle.Side("#Z", List.of(2), 0, 4.0));
    }

    private TopBattleService service() {
        return new TopBattleService(new TopBattleStore(dir), Clock.fixed(NOW, ZoneOffset.UTC), 1000);
    }

    @Test
    void 渡された対戦を足して_同じ対戦は1つにし_1週間より前は集計から外す() throws IOException {
        TopBattleService service = service();
        service.publish(List.of(battle("2026-10-17T00:00:00Z", "#A", 1), battle("2026-10-10T00:00:00Z", "#A", 1)));
        service.publish(List.of(battle("2026-10-17T00:00:00Z", "#A", 1), battle("2026-10-16T00:00:00Z", "#B", 1)));

        assertEquals(2, service.matchups().orElseThrow().battles());
        assertEquals(2, service.elixirLeak().orElseThrow().battles());
        // ファイルには1週間より前の対戦も残す。
        assertEquals(3, new TopBattleStore(dir).readSince(Instant.EPOCH).size());
    }

    @Test
    void 起動時に直近1週間の対戦を読み込む() {
        service().publish(List.of(battle("2026-10-17T00:00:00Z", "#A", 1), battle("2026-10-01T00:00:00Z", "#A", 1)));

        TopBattleService restarted = service();

        assertEquals(1, restarted.matchups().orElseThrow().battles());
        assertFalse(restarted.awaitingFirstCollection());
    }

    @Test
    void 対戦が無いうちは集計が無く_初回の集計を待つのは起動後に一度も渡されていない間だけ() {
        TopBattleService service = service();
        assertTrue(service.matchups().isEmpty());
        assertTrue(service.elixirLeak().isEmpty());
        assertTrue(service.awaitingFirstCollection());

        // 集めても0戦だったときに、集計を休みなく繰り返さない。
        service.publish(List.of());

        assertTrue(service.matchups().isEmpty());
        assertFalse(service.awaitingFirstCollection());
    }
}
