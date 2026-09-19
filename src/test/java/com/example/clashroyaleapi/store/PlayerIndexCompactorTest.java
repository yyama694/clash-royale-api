package com.example.clashroyaleapi.store;

import com.example.clashroyaleapi.domain.PlayerNameMatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

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

    @Test
    void 名前の索引を作り_名前を変えた人は旧名で見つからなくなる() throws IOException {
        inbox("20260919-10.tsv", "#AAA\tOld Name\t2026-09-19T10:00:00Z", "#BBB\t<c3>OLD　NAME\t2026-09-19T10:30:00Z");
        new PlayerIndexCompactor(dir, CLOCK).compact();

        assertEquals(List.of("#BBB", "#AAA"), tagsFound("old name"));

        inbox("20260919-11.tsv", "#AAA\tNew Name\t2026-09-19T11:00:00Z");
        new PlayerIndexCompactor(dir, CLOCK).compact();

        assertEquals(List.of("#BBB"), tagsFound("old name"));
        assertEquals(List.of(new PlayerNameMatch("#AAA", "New Name", Instant.parse("2026-09-19T11:00:00Z"))),
                new PlayerNameIndex(dir).search("new name", 0, 50).exact());
    }

    @Test
    void 名前の索引が無ければby_tagから全件作り直す() throws IOException {
        // by-nameを作る前の版で整理したby-tagだけがある状態(本番の初回)を再現する。
        inbox("20260919-10.tsv", "#AAA\tAlice\t2026-09-19T10:00:00Z");
        new PlayerIndexCompactor(dir, CLOCK).compact();
        deleteRecursively(dir.resolve("by-name"));

        new PlayerIndexCompactor(dir, CLOCK).compact();

        assertEquals(List.of("#AAA"), tagsFound("alice"));
        assertFalse(Files.exists(dir.resolve("by-name.rebuild")));
    }

    @Test
    void 目次の無い以前の形式の索引は作り直して古いファイルを消す() throws IOException {
        inbox("20260919-10.tsv", "#AAA\tAlice\t2026-09-19T10:00:00Z");
        new PlayerIndexCompactor(dir, CLOCK).compact();
        deleteRecursively(dir.resolve("by-name"));
        Files.createDirectories(dir.resolve("by-name"));
        Files.write(dir.resolve("by-name/0123.tsv"), List.of("alice\t#AAA\tAlice\t2026-09-19T10:00:00Z"),
                StandardCharsets.UTF_8);

        new PlayerIndexCompactor(dir, CLOCK).compact();

        assertEquals(List.of("#AAA"), tagsFound("alice"));
        assertFalse(Files.exists(dir.resolve("by-name/0123.tsv")));
    }

    @Test
    void 名前の索引は複数のファイルに分かれても引ける() throws IOException {
        inbox("20260919-10.tsv", "#AAA\tAlice\t2026-09-19T10:00:00Z", "#BBB\tAlicia\t2026-09-19T10:00:00Z",
                "#CCC\tBob\t2026-09-19T10:00:00Z", "#DDD\tCarol\t2026-09-19T10:00:00Z",
                "#EEE\tDave\t2026-09-19T10:00:00Z");

        new PlayerIndexCompactor(dir, CLOCK, 2).compact();

        assertEquals(List.of("#EEE"), tagsFound("dave"));
        assertEquals(List.of("#AAA", "#BBB"),
                new PlayerNameIndex(dir).search("ali", 0, 50).prefix().stream().map(PlayerNameMatch::tag).toList());
    }

    @Test
    void 名前の索引に反映し終える前に落ちたら次回は全件作り直す() throws IOException {
        inbox("20260919-10.tsv", "#AAA\tAlice\t2026-09-19T10:00:00Z");
        new PlayerIndexCompactor(dir, CLOCK).compact();
        // by-tagは新しい名前に書き換わったが、by-nameは古いまま落ちた状態を再現する。
        Files.write(dir.resolve("by-tag").resolve(String.format("%04d.tsv", PlayerIndexCompactor.shardOf("#AAA"))),
                List.of("#AAA\tAlicia\t2026-09-19T11:00:00Z"), StandardCharsets.UTF_8);
        Files.createFile(dir.resolve("by-name.rebuild"));

        new PlayerIndexCompactor(dir, CLOCK).compact();

        assertEquals(List.of(), tagsFound("alice"));
        assertEquals(List.of("#AAA"), tagsFound("alicia"));
    }

    private List<String> tagsFound(String name) {
        return new PlayerNameIndex(dir).search(name, 0, 50).exact().stream().map(PlayerNameMatch::tag).toList();
    }

    private static void deleteRecursively(Path path) throws IOException {
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
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
