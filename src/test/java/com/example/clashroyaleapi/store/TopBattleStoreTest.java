package com.example.clashroyaleapi.store;

import com.example.clashroyaleapi.domain.TopBattle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopBattleStoreTest {

    @TempDir
    Path dir;

    private static TopBattle battle(String time, String firstTag) {
        return new TopBattle(Instant.parse(time),
                new TopBattle.Side(firstTag, List.of(26000000, 26000001), 1, 2.35),
                new TopBattle.Side("#ZZZ", List.of(28000000), 0, 0.0));
    }

    @Test
    void 対戦の日付ごとのファイルに分けて書き_同じ対戦は1つにまとめる() throws IOException {
        TopBattleStore store = new TopBattleStore(dir.resolve("top-battles"));
        TopBattle first = battle("2026-10-16T23:59:59Z", "#A");
        TopBattle second = battle("2026-10-17T00:00:00Z", "#A");

        store.add(List.of(first, second));
        store.add(List.of(second, battle("2026-10-17T05:00:00Z", "#B")));

        assertTrue(Files.exists(dir.resolve("top-battles/2026-10-16.tsv")));
        assertEquals(2, Files.readAllLines(dir.resolve("top-battles/2026-10-17.tsv")).size());
        assertEquals(List.of(first, second, battle("2026-10-17T05:00:00Z", "#B")),
                store.readSince(Instant.parse("2026-10-16T00:00:00Z")));
    }

    @Test
    void その時刻より前の対戦は読まない() throws IOException {
        TopBattleStore store = new TopBattleStore(dir);
        store.add(List.of(battle("2026-10-10T12:00:00Z", "#A"), battle("2026-10-17T11:00:00Z", "#A"),
                battle("2026-10-17T13:00:00Z", "#A")));

        assertEquals(List.of(battle("2026-10-17T13:00:00Z", "#A")),
                store.readSince(Instant.parse("2026-10-17T12:00:00Z")));
    }

    @Test
    void 読めない行と関係ないファイルは飛ばす() throws IOException {
        TopBattleStore store = new TopBattleStore(dir);
        store.add(List.of(battle("2026-10-17T13:00:00Z", "#A")));
        Files.writeString(dir.resolve("2026-10-17.tsv"), "broken\n", StandardOpenOption.APPEND);
        Files.writeString(dir.resolve("notes.txt"), "memo");

        assertEquals(List.of(battle("2026-10-17T13:00:00Z", "#A")),
                store.readSince(Instant.parse("2026-10-01T00:00:00Z")));
    }

    @Test
    void まだ何も無ければ空() throws IOException {
        assertTrue(new TopBattleStore(dir.resolve("none")).readSince(Instant.EPOCH).isEmpty());
    }
}
