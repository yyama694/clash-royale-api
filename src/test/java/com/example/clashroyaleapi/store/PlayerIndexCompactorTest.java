package com.example.clashroyaleapi.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerIndexCompactorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-19T18:30:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    @Test
    void 同じタグは最後に確認した名前だけを残しinboxを消す() throws IOException {
        inbox("20260919-10.tsv", "#AAA\tOld\t2026-09-19T10:00:00Z", "#AAA\tOld\t2026-09-19T10:05:00Z");
        inbox("crawl-20260919-11.tsv", "#AAA\tNew\t2026-09-19T11:00:00Z");

        new PlayerIndexCompactor(dir, CLOCK).compact();

        assertEquals(List.of("#AAA\tNew\t2026-09-19T11:00:00Z"), byTag("#AAA"));
        assertFalse(Files.exists(dir.resolve("inbox/20260919-10.tsv")));
        assertFalse(Files.exists(dir.resolve("inbox/crawl-20260919-11.tsv")));
    }

    @Test
    void 既存のby_tagに追加し古い確認日時では上書きしない() throws IOException {
        inbox("20260919-10.tsv", "#AAA\tNew\t2026-09-19T10:00:00Z");
        new PlayerIndexCompactor(dir, CLOCK).compact();

        inbox("20260919-11.tsv", "#AAA\tOld\t2026-09-18T10:00:00Z", "#BBB\tOther\t2026-09-19T11:00:00Z");
        new PlayerIndexCompactor(dir, CLOCK).compact();

        assertEquals(List.of("#AAA\tNew\t2026-09-19T10:00:00Z"), byTag("#AAA"));
        assertTrue(byTag("#BBB").contains("#BBB\tOther\t2026-09-19T11:00:00Z"));
    }

    @Test
    void 同じinboxを2度取り込んでも結果は変わらない() throws IOException {
        inbox("20260919-10.tsv", "#AAA\tName\t2026-09-19T10:00:00Z");
        Path copy = dir.resolve("copy.tsv");
        Files.copy(dir.resolve("inbox/20260919-10.tsv"), copy);
        new PlayerIndexCompactor(dir, CLOCK).compact();

        Files.copy(copy, dir.resolve("inbox/20260919-10.tsv"));
        new PlayerIndexCompactor(dir, CLOCK).compact();

        assertEquals(List.of("#AAA\tName\t2026-09-19T10:00:00Z"), byTag("#AAA"));
    }

    @Test
    void 今の時刻のinboxはまだ追記され得るので取り込まない() throws IOException {
        inbox("crawl-20260919-18.tsv", "#AAA\tName\t2026-09-19T18:10:00Z");

        new PlayerIndexCompactor(dir, CLOCK).compact();

        assertTrue(Files.exists(dir.resolve("inbox/crawl-20260919-18.tsv")));
        assertFalse(Files.exists(dir.resolve("by-tag")));
    }

    @Test
    void by_tagのファイルはタグ順に並べる() throws IOException {
        // 同じファイルに振り分けられるタグの組を探す(ハッシュ値で分けるため、任意の2つが同じファイルとは限らない)。
        String first = "#2";
        String second = null;
        for (int i = 3; second == null; i++) {
            String candidate = "#" + Integer.toString(i, 36).toUpperCase();
            if (PlayerIndexCompactor.shardOf(candidate) == PlayerIndexCompactor.shardOf(first)
                    && candidate.compareTo(first) > 0) {
                second = candidate;
            }
        }
        inbox("20260919-10.tsv", second + "\tB\t2026-09-19T10:00:00Z", first + "\tA\t2026-09-19T10:00:00Z");

        new PlayerIndexCompactor(dir, CLOCK).compact();

        assertEquals(List.of(first + "\tA\t2026-09-19T10:00:00Z", second + "\tB\t2026-09-19T10:00:00Z"), byTag(first));
    }

    private void inbox(String fileName, String... lines) throws IOException {
        Files.createDirectories(dir.resolve("inbox"));
        Files.write(dir.resolve("inbox").resolve(fileName), List.of(lines), StandardCharsets.UTF_8);
    }

    private List<String> byTag(String tag) throws IOException {
        return Files.readAllLines(dir.resolve("by-tag")
                .resolve(String.format("%04d.tsv", PlayerIndexCompactor.shardOf(tag))), StandardCharsets.UTF_8);
    }
}
