package com.example.clashroyaleapi.store;

import com.example.clashroyaleapi.domain.PlayerNameMatch;
import com.example.clashroyaleapi.domain.PlayerNameSearch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerNameIndexTest {

    @TempDir
    Path dir;

    @Test
    void 正規化は装飾タグ_全角半角_大文字小文字_空白の違いを吸収する() {
        // ルールを変えると索引の作り直しになるので、ここで固定する。
        assertEquals("abc 1", PlayerNameIndex.normalize("<c2>ＡＢＣ　 １"));
        assertEquals("taro yamada", PlayerNameIndex.normalize("  Taro   YAMADA "));
        assertEquals("かいと", PlayerNameIndex.normalize("かいと"));
        assertEquals("カイト", PlayerNameIndex.normalize("ｶｲﾄ"));
        assertEquals("", PlayerNameIndex.normalize(null));
    }

    @Test
    void 完全一致は全員を数え最近確認した順に並べ_残りの枠を前方一致で埋める() throws IOException {
        write(2,
                add("bob", "#AAA", "Bob", "2026-09-18T10:00:00Z"),
                add("bob", "#BBB", "<c1>BOB", "2026-09-19T10:00:00Z"),
                add("bobby", "#CCC", "Bobby", "2026-09-19T11:00:00Z"),
                add("bob 2", "#DDD", "Bob 2", "2026-09-19T11:00:00Z"),
                add("bo", "#EEE", "Bo", "2026-09-19T11:00:00Z"),
                add("carol", "#FFF", "Carol", "2026-09-19T11:00:00Z"));

        PlayerNameSearch result = new PlayerNameIndex(dir).search(" BOB ", 0, 50);

        assertEquals(List.of("#BBB", "#AAA"), tags(result.exact()));
        assertEquals(2, result.exactTotal());
        assertEquals(List.of("#DDD", "#CCC"), tags(result.prefix()));
        assertFalse(result.morePrefix());
    }

    @Test
    void 前方一致は枠の分だけ読み_表示しきれない人がいることを返す() throws IOException {
        write(2, add("a", "#A0", "a", "2026-09-19T10:00:00Z"),
                add("ab", "#A1", "ab", "2026-09-19T10:00:00Z"),
                add("ac", "#A2", "ac", "2026-09-19T10:00:00Z"),
                add("ad", "#A3", "ad", "2026-09-19T10:00:00Z"));

        PlayerNameSearch result = new PlayerNameIndex(dir).search("a", 0, 3);

        assertEquals(List.of("#A0"), tags(result.exact()));
        assertEquals(List.of("#A1", "#A2"), tags(result.prefix()));
        assertTrue(result.morePrefix());
    }

    @Test
    void 完全一致だけでページが埋まるときは前方一致を出さず_あることだけ返す() throws IOException {
        List<String> diffs = new ArrayList<>();
        IntStream.range(0, 5).forEach(i -> diffs.add(add("alex", "#T" + i, "Alex", "2026-09-19T1" + i + ":00:00Z")));
        diffs.add(add("alexa", "#X", "Alexa", "2026-09-19T10:00:00Z"));
        write(2, diffs.toArray(String[]::new));
        PlayerNameIndex index = new PlayerNameIndex(dir);

        PlayerNameSearch first = index.search("alex", 0, 2);
        PlayerNameSearch last = index.search("alex", 4, 2);

        assertEquals(List.of("#T4", "#T3"), tags(first.exact()));
        assertEquals(5, first.exactTotal());
        assertTrue(first.prefix().isEmpty());
        assertTrue(first.morePrefix());
        assertEquals(List.of("#T0"), tags(last.exact()));
    }

    @Test
    void 差分で名前を変えた人は旧名で見つからず_ファイルが大きくなれば分ける() throws IOException {
        List<String> diffs = new ArrayList<>();
        IntStream.range(0, 10).forEach(i -> diffs.add(add("name" + i, "#T" + i, "Name" + i, "2026-09-19T10:00:00Z")));
        write(2, diffs.toArray(String[]::new));

        write(false, 2, remove("name3", "#T3"), add("zeta", "#T3", "Zeta", "2026-09-19T11:00:00Z"),
                add("name30", "#N1", "Name30", "2026-09-19T11:00:00Z"),
                add("name31", "#N2", "Name31", "2026-09-19T11:00:00Z"),
                add("name32", "#N3", "Name32", "2026-09-19T11:00:00Z"));
        PlayerNameIndex index = new PlayerNameIndex(dir);

        assertEquals(0, index.search("name3", 0, 50).exactTotal());
        assertEquals(List.of("#N1", "#N2", "#N3"), tags(index.search("name3", 0, 50).prefix()));
        assertEquals(List.of("#T3"), tags(index.search("zeta", 0, 50).exact()));
        // name0〜9 から name3 が抜け、name30〜32 が増えた12人。
        assertEquals(12, index.search("name", 0, 50).prefix().size());
        // 4行(2の2倍)に達したファイルは分けられている。目次に載っていない差し替え前のファイルは消えている。
        int indexedFiles = PlayerNameIndex.readIndex(dir.resolve("by-name")).size();
        assertTrue(indexedFiles > 1);
        assertEquals(indexedFiles + 1, fileCount());
    }

    @Test
    void 索引がまだ無ければ空を返す() {
        assertTrue(new PlayerNameIndex(dir).search("bob", 0, 50).isEmpty());
        assertTrue(new PlayerNameIndex(dir).search("  ", 0, 50).isEmpty());
    }

    private void write(int chunkLines, String... diffs) throws IOException {
        write(true, chunkLines, diffs);
    }

    private void write(boolean fromScratch, int chunkLines, String... diffs) throws IOException {
        Path file = dir.resolve("diffs.tsv");
        Files.write(file, List.of(diffs), StandardCharsets.UTF_8);
        new PlayerNameIndexWriter(dir.resolve("by-name"), chunkLines)
                .apply(file, dir.resolve("work"), fromScratch, "run" + System.nanoTime());
    }

    private static String add(String key, String tag, String name, String lastSeen) {
        return key + "\t" + tag + "\t+\t" + name + "\t" + lastSeen;
    }

    private static String remove(String key, String tag) {
        return key + "\t" + tag + "\t-";
    }

    private static List<String> tags(List<PlayerNameMatch> matches) {
        return matches.stream().map(PlayerNameMatch::tag).toList();
    }

    private long fileCount() throws IOException {
        try (Stream<Path> files = Files.list(dir.resolve("by-name"))) {
            return files.count();
        }
    }
}
