package com.example.clashroyaleapi.store;

import com.example.clashroyaleapi.domain.RankingSnapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankingSnapshotStoreTest {

    @TempDir
    Path dir;

    private static final RankingSnapshot SNAPSHOT = new RankingSnapshot(Instant.parse("2026-10-06T09:16:00Z"),
            "2026-09", List.of(new RankingSnapshot.Entry(1, "#ABC", "Miku", 3001),
                    new RankingSnapshot.Entry(2, "#DEF", "名前 スペース", 2999)));

    @Test
    void 書いた記録をそのまま読み戻せる() throws IOException {
        RankingSnapshotStore store = new RankingSnapshotStore(dir);

        store.write(SNAPSHOT);

        assertEquals(Optional.of(SNAPSHOT), store.read(SNAPSHOT.takenAt()));
        assertTrue(Files.exists(dir.resolve("2026-10-06").resolve("091600.tsv")));
    }

    @Test
    void 名前のタブと改行は空白にして保存する() throws IOException {
        RankingSnapshotStore store = new RankingSnapshotStore(dir);
        RankingSnapshot snapshot = new RankingSnapshot(Instant.parse("2026-10-06T09:16:00Z"), null,
                List.of(new RankingSnapshot.Entry(1, "#ABC", "a\tb\nc", 3001)));

        store.write(snapshot);

        assertEquals("a b c", store.read(snapshot.takenAt()).orElseThrow().entries().getFirst().name());
        assertEquals(null, store.read(snapshot.takenAt()).orElseThrow().finishedSeason());
    }

    @Test
    void 一覧は日をまたいでも古い順で1位だけを読む() throws IOException {
        RankingSnapshotStore store = new RankingSnapshotStore(dir);
        RankingSnapshot earlier = new RankingSnapshot(Instant.parse("2026-10-05T23:46:00Z"), "2026-09",
                List.of(new RankingSnapshot.Entry(1, "#XYZ", "Taro", 2990)));
        store.write(SNAPSHOT);
        store.write(earlier);

        assertEquals(List.of(
                new RankingSnapshotStore.Summary(earlier.takenAt(), "2026-09", earlier.entries().getFirst()),
                new RankingSnapshotStore.Summary(SNAPSHOT.takenAt(), "2026-09", SNAPSHOT.entries().getFirst())),
                store.summaries());
    }

    @Test
    void 読めないファイルは一覧から飛ばす() throws IOException {
        RankingSnapshotStore store = new RankingSnapshotStore(dir);
        store.write(SNAPSHOT);
        Files.writeString(dir.resolve("2026-10-06").resolve("100000.tsv"), "broken\n");

        assertEquals(1, store.summaries().size());
    }

    @Test
    void まだ何も記録していなければ空() throws IOException {
        RankingSnapshotStore store = new RankingSnapshotStore(dir.resolve("missing"));

        assertEquals(List.of(), store.summaries());
        assertEquals(Optional.empty(), store.read(SNAPSHOT.takenAt()));
    }
}
