package com.example.clashroyaleapi.store;

import com.example.clashroyaleapi.config.PlayerIndexProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * inbox/ に溜まった発見ログを、タグごとの正本 by-tag/ にまとめる(「プレイヤー名検索（検討中）.md」参照)。
 * <ul>
 *   <li>by-tag/NNNN.tsv … tag \t name \t 最終確認日時(タグ順)。タグのハッシュ値で1024個に分ける</li>
 * </ul>
 * 常に1ファイル分ずつ処理するので、全体が数十GBになってもメモリは1ファイル分(最大でも数十MB)で済む。
 * 同じinboxを2度取り込んでも結果は変わらないため、途中で落ちた場合は次回そのままやり直せばよい。
 */
@Component
public class PlayerIndexCompactor {

    private static final Logger log = LoggerFactory.getLogger(PlayerIndexCompactor.class);

    // 変えると全件の振り分け直しになるので固定する。
    static final int TAG_SHARDS = 1024;
    // 1024個のファイルを同時に開くとOSの上限(既定1024)に掛かり得るため、振り分け先ごとにメモリに溜めてまとめて追記する。
    private static final int FLUSH_CHARS = 16 * 1024;

    private static final DateTimeFormatter CURRENT_HOUR =
            DateTimeFormatter.ofPattern("yyyyMMdd-HH").withZone(ZoneOffset.UTC);

    private final Path inboxDir;
    private final Path byTagDir;
    private final Path workDir;
    private final Clock clock;

    @Autowired
    public PlayerIndexCompactor(PlayerIndexProperties properties) {
        this(Path.of(properties.dir()), Clock.systemUTC());
    }

    PlayerIndexCompactor(Path dir, Clock clock) {
        this.inboxDir = dir.resolve("inbox");
        this.byTagDir = dir.resolve("by-tag");
        this.workDir = dir.resolve("work-compact");
        this.clock = clock;
    }

    /**
     * 巡回(Crawler)と同じスケジューラーのスレッドで動くため、実行中は巡回が止まる。
     * 低スペックのVMでディスクとCPUを取り合わないので、むしろ都合がよい。
     */
    @Scheduled(cron = "${player-index.compact-cron}", zone = "UTC")
    public void compactOnSchedule() {
        try {
            compact();
        } catch (IOException e) {
            log.warn("player index compaction failed (will retry next time): {}", e.toString());
        }
    }

    synchronized void compact() throws IOException {
        List<Path> inbox = closedInboxFiles();
        if (inbox.isEmpty()) {
            return;
        }
        long started = clock.millis();
        deleteRecursively(workDir);
        Files.createDirectories(workDir);
        Files.createDirectories(byTagDir);

        long lines = split(inbox);
        long players = 0;
        for (int shard = 0; shard < TAG_SHARDS; shard++) {
            players += merge(shard);
        }
        for (Path file : inbox) {
            Files.delete(file);
        }
        deleteRecursively(workDir);
        log.info("player index compacted: {} inbox files, {} lines, {} players in updated by-tag files ({} ms)",
                inbox.size(), lines, players, clock.millis() - started);
    }

    static int shardOf(String tag) {
        return Math.floorMod(tag.hashCode(), TAG_SHARDS);
    }

    // 今の時刻のファイルはまだ追記され得るので対象にしない(閲覧分・巡回分とも)。
    private List<Path> closedInboxFiles() throws IOException {
        if (!Files.isDirectory(inboxDir)) {
            return List.of();
        }
        String currentHour = CURRENT_HOUR.format(clock.instant());
        try (Stream<Path> files = Files.list(inboxDir)) {
            return files
                    .filter(file -> file.getFileName().toString().endsWith(".tsv"))
                    .filter(file -> !file.getFileName().toString().contains(currentHour))
                    .sorted()
                    .toList();
        }
    }

    private long split(List<Path> inbox) throws IOException {
        StringBuilder[] buffers = new StringBuilder[TAG_SHARDS];
        long lines = 0;
        for (Path file : inbox) {
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int tab = line.indexOf('\t');
                    if (tab <= 0) {
                        continue;
                    }
                    int shard = shardOf(line.substring(0, tab));
                    if (buffers[shard] == null) {
                        buffers[shard] = new StringBuilder();
                    }
                    buffers[shard].append(line).append('\n');
                    if (buffers[shard].length() >= FLUSH_CHARS) {
                        appendTo(splitFile(shard), buffers[shard]);
                    }
                    lines++;
                }
            }
        }
        for (int shard = 0; shard < TAG_SHARDS; shard++) {
            if (buffers[shard] != null) {
                appendTo(splitFile(shard), buffers[shard]);
            }
        }
        return lines;
    }

    /** by-tagの1ファイルに、振り分けたinboxの行を反映する。同じタグは最後に確認された名前を残す。反映後の件数を返す。 */
    private int merge(int shard) throws IOException {
        Path target = byTagDir.resolve(String.format("%04d.tsv", shard));
        Path split = splitFile(shard);
        if (!Files.exists(split)) {
            return 0;
        }
        Map<String, String[]> latest = new HashMap<>();
        for (Path source : new Path[] {target, split}) {
            if (!Files.exists(source)) {
                continue;
            }
            try (BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] fields = line.split("\t", -1);
                    if (fields.length != 3 || fields[0].isEmpty()) {
                        continue;
                    }
                    // 確認日時はUTC・秒単位のISO形式(例: 2026-09-18T10:15:30Z)で揃っているので、文字列の比較で新旧が分かる。
                    latest.merge(fields[0], fields, (a, b) -> b[2].compareTo(a[2]) > 0 ? b : a);
                }
            }
        }
        List<String[]> rows = new ArrayList<>(latest.values());
        rows.sort(Comparator.comparing(row -> row[0]));
        Path tmp = byTagDir.resolve(target.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            for (String[] row : rows) {
                writer.write(row[0] + "\t" + row[1] + "\t" + row[2] + "\n");
            }
        }
        // 書きかけのファイルを検索などが読まないよう、書き終えてから置き換える。
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return rows.size();
    }

    private Path splitFile(int shard) {
        return workDir.resolve(shard + ".tsv");
    }

    private static void appendTo(Path file, StringBuilder buffer) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.append(buffer);
        }
        buffer.setLength(0);
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
