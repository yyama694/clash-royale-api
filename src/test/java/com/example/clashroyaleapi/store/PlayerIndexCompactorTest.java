package com.example.clashroyaleapi.store;

import com.example.clashroyaleapi.domain.PlayerNameMatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.support.CronExpression;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    @Test
    void 正規化の規則を変えた後は全件作り直して規則の版を記録する() throws IOException {
        inbox("20260919-10.tsv", "#AAA\tJosé\t2026-09-19T10:00:00Z");
        new PlayerIndexCompactor(dir, CLOCK).compact();
        assertEquals(List.of("#AAA"), tagsFound("jose"));
        // 版を記録する前の(古い規則の)索引を再現する。by-tagだけを変えておき、作り直されたことを確かめる。
        Files.delete(dir.resolve("by-name.version"));
        Files.write(dir.resolve("by-tag").resolve(String.format("%04d.tsv", PlayerIndexCompactor.shardOf("#AAA"))),
                List.of("#AAA\tJosefina\t2026-09-19T11:00:00Z"), StandardCharsets.UTF_8);

        new PlayerIndexCompactor(dir, CLOCK).compact();

        assertEquals(List.of("#AAA"), tagsFound("josefina"));
        assertEquals(Integer.toString(PlayerNameIndex.NORMALIZATION_VERSION),
                Files.readString(dir.resolve("by-name.version"), StandardCharsets.UTF_8));
    }

    @Test
    void 流す形は全行をメモリに載せる従来の方法と同じ結果になる() throws IOException {
        for (long seed = 1; seed <= 300; seed++) {
            Random random = new Random(seed);
            Map<String, String> baseNames = new TreeMap<>();
            List<String> base = randomBase(random, baseNames);
            List<String> updates = randomUpdates(random, baseNames);
            boolean baseExists = seed % 10 != 0;
            boolean withDiffs = seed % 5 != 0;
            Path inMemory = prepare("m" + seed, baseExists ? base : null, updates);
            Path streamed = prepare("s" + seed, baseExists ? base : null, updates);
            StringWriter inMemoryDiffs = withDiffs ? new StringWriter() : null;
            StringWriter streamedDiffs = withDiffs ? new StringWriter() : null;

            int expectedRows = PlayerIndexCompactor.mergeInMemory(inMemory.resolve("0001.tsv"),
                    inMemory.resolve("split.tsv"), inMemoryDiffs);
            int rows = PlayerIndexCompactor.mergeSorted(streamed.resolve("0001.tsv"),
                    streamed.resolve("split.tsv"), streamedDiffs);

            String message = "seed=" + seed;
            assertEquals(expectedRows, rows, message);
            assertEquals(Files.readString(inMemory.resolve("0001.tsv"), StandardCharsets.UTF_8),
                    Files.readString(streamed.resolve("0001.tsv"), StandardCharsets.UTF_8), message);
            if (withDiffs) {
                assertEquals(sortedLines(inMemoryDiffs), sortedLines(streamedDiffs), message);
            }
            assertFalse(Files.exists(streamed.resolve("0001.tsv.tmp")), message);
        }
    }

    @Test
    void 元のファイルがタグ順でなければ流す形は何も置き換えず_従来の方法に切り替えて同じ結果になる() throws IOException {
        // 想定外の状態: 降順に並び、同じタグが重なっている。従来の方法は、重なった行は後の行を残して並べ直す。
        List<String> base = List.of("#B\tb\t2026-11-10T00:00:00Z", "#A\ta\t2026-11-10T00:00:00Z",
                "#A\ta2\t2026-11-11T00:00:00Z", "#C\tc\t2026-11-10T00:00:00Z");
        List<String> updates = List.of("#A\tnew\t2026-11-12T00:00:00Z", "#D\td\t2026-11-12T00:00:00Z");
        Path inMemory = prepare("m", base, updates);
        Path refused = prepare("r", base, updates);
        Path switched = prepare("s", base, updates);
        StringWriter inMemoryDiffs = new StringWriter();
        StringWriter switchedDiffs = new StringWriter();

        PlayerIndexCompactor.mergeInMemory(inMemory.resolve("0001.tsv"), inMemory.resolve("split.tsv"), inMemoryDiffs);
        int refusedRows = PlayerIndexCompactor.mergeSorted(refused.resolve("0001.tsv"), refused.resolve("split.tsv"),
                new StringWriter());
        int switchedRows = PlayerIndexCompactor.mergeShard(switched.resolve("0001.tsv"), switched.resolve("split.tsv"),
                switchedDiffs);

        assertEquals(PlayerIndexCompactor.NOT_SORTED, refusedRows);
        assertEquals(Files.readAllLines(refused.resolve("0001.tsv"), StandardCharsets.UTF_8), base);
        assertFalse(Files.exists(refused.resolve("0001.tsv.tmp")));
        assertEquals(Files.readString(inMemory.resolve("0001.tsv"), StandardCharsets.UTF_8),
                Files.readString(switched.resolve("0001.tsv"), StandardCharsets.UTF_8));
        assertEquals(List.of("#A\tnew\t2026-11-12T00:00:00Z", "#B\tb\t2026-11-10T00:00:00Z",
                "#C\tc\t2026-11-10T00:00:00Z", "#D\td\t2026-11-12T00:00:00Z"),
                Files.readAllLines(switched.resolve("0001.tsv"), StandardCharsets.UTF_8));
        assertEquals(4, switchedRows);
        assertEquals(sortedLines(inMemoryDiffs), sortedLines(switchedDiffs));
    }

    @Test
    void 既定の実行時刻はUTCの1時台から3時間ごとの5分() throws IOException {
        String yml = new String(new ClassPathResource("application.yml").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("compact-cron: \\$\\{PLAYER_INDEX_COMPACT_CRON:([^}]+)}").matcher(yml);
        assertTrue(matcher.find());
        CronExpression cron = CronExpression.parse(matcher.group(1));

        List<String> times = new ArrayList<>();
        ZonedDateTime time = ZonedDateTime.parse("2026-11-19T10:17:28Z");
        for (int i = 0; i < 8; i++) {
            time = cron.next(time);
            times.add(time.toInstant().toString());
        }

        assertEquals(List.of("2026-11-19T13:05:00Z", "2026-11-19T16:05:00Z", "2026-11-19T19:05:00Z",
                "2026-11-19T22:05:00Z", "2026-11-20T01:05:00Z", "2026-11-20T04:05:00Z", "2026-11-20T07:05:00Z",
                "2026-11-20T10:05:00Z"), times);
    }

    private static final String[] TAG_CHARS = {"0", "2", "8", "9", "P", "Y", "L", "Q", "G", "R", "J", "C", "U", "V"};
    private static final String[] NAMES = {"Alice", "alice", "José", "ゆうき", "😀Smile", "A B", "<c3>OLD　NAME", "",
            "Ünal", "Kılıç"};
    // 同じ確認日時(新旧が決まらない)と、空の確認日時も混ぜる。
    private static final String[] TIMES = {"2026-11-10T00:00:00Z", "2026-11-10T00:00:01Z", "2026-11-11T12:00:00Z",
            "2026-11-12T00:00:00Z", "2026-11-13T09:30:00Z", ""};
    // 3列でない行・タグが空の行は、どちらの方法でも捨てられる。
    private static final String[] BROKEN = {"", "garbage", "#X\tonly-two", "#Y\ta\tb\tc", "\tname\t2026-11-10T00:00:00Z"};

    private static String randomTag(Random random) {
        StringBuilder tag = new StringBuilder("#");
        for (int i = 0; i < 3; i++) {
            tag.append(TAG_CHARS[random.nextInt(TAG_CHARS.length)]);
        }
        return tag.toString();
    }

    // タグ順の元のファイルの行(壊れた行をところどころに挟む)。baseNamesにタグと名前を入れる。
    private static List<String> randomBase(Random random, Map<String, String> baseNames) {
        int size = random.nextInt(400);
        while (baseNames.size() < size) {
            baseNames.put(randomTag(random), NAMES[random.nextInt(NAMES.length)]);
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, String> entry : baseNames.entrySet()) {
            lines.add(entry.getKey() + "\t" + entry.getValue() + "\t" + TIMES[random.nextInt(TIMES.length)]);
            if (random.nextInt(20) == 0) {
                lines.add(BROKEN[random.nextInt(BROKEN.length)]);
            }
        }
        return lines;
    }

    // 振り分けたinboxの行。元にあるタグ(同じ名前・別の名前)と新しいタグを混ぜ、同じタグが何度も出る。
    private static List<String> randomUpdates(Random random, Map<String, String> baseNames) {
        List<String> baseTags = new ArrayList<>(baseNames.keySet());
        List<String> lines = new ArrayList<>();
        int size = random.nextInt(300);
        for (int i = 0; i < size; i++) {
            if (random.nextInt(25) == 0) {
                lines.add(BROKEN[random.nextInt(BROKEN.length)]);
                continue;
            }
            boolean existing = !baseTags.isEmpty() && random.nextBoolean();
            String tag = existing ? baseTags.get(random.nextInt(baseTags.size())) : randomTag(random);
            String name = existing && random.nextInt(5) < 2 ? baseNames.get(tag) : NAMES[random.nextInt(NAMES.length)];
            lines.add(tag + "\t" + name + "\t" + TIMES[random.nextInt(TIMES.length)]);
        }
        return lines;
    }

    // 元のファイル(baseがnullなら作らない)と、振り分けた行を、新しいフォルダに置く。
    private Path prepare(String name, List<String> base, List<String> updates) throws IOException {
        Path folder = Files.createDirectories(dir.resolve(name));
        if (base != null) {
            Files.write(folder.resolve("0001.tsv"), base, StandardCharsets.UTF_8);
        }
        Files.write(folder.resolve("split.tsv"), updates, StandardCharsets.UTF_8);
        return folder;
    }

    private static List<String> sortedLines(StringWriter diffs) {
        return diffs.toString().lines().sorted().toList();
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
