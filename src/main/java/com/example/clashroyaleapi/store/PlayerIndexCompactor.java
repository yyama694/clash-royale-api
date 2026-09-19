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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * inbox/ に溜まった発見ログを、タグごとの正本 by-tag/ にまとめ、名前検索用の索引 by-name/ に反映する
 * (「プレイヤー名検索（検討中）.md」参照)。
 * <ul>
 *   <li>by-tag/NNNN.tsv … tag \t name \t 最終確認日時(タグ順)。タグのハッシュ値で1024個に分ける</li>
 *   <li>by-name/ … {@link PlayerNameIndex} を参照</li>
 * </ul>
 * by-name には、by-tag で変わった行だけを差分(旧名の削除・新名の追加)として反映する。
 * 旧名を消さないと、名前を変えた人が旧名で検索しても見つかり続けるため。
 * 常に1ファイル分ずつ(by-nameは外部ソートで一定行数ずつ)処理するので、全体が数十GBになってもメモリは数十MBで済む。
 * 同じinboxを2度取り込んでも結果は変わらないため、途中で落ちた場合は次回そのままやり直せばよい。
 */
@Component
public class PlayerIndexCompactor {

    private static final Logger log = LoggerFactory.getLogger(PlayerIndexCompactor.class);

    // 変えると全件の振り分け直しになるので固定する。
    static final int TAG_SHARDS = 1024;
    // 振り分け先ごとにメモリに溜める上限。
    private static final int TAG_FLUSH_CHARS = 16 * 1024;

    private static final DateTimeFormatter CURRENT_HOUR =
            DateTimeFormatter.ofPattern("yyyyMMdd-HH").withZone(ZoneOffset.UTC);

    private final Path inboxDir;
    private final Path byTagDir;
    private final Path byNameDir;
    private final Path nameRebuildMarker;
    private final Path workDir;
    private final PlayerNameIndexWriter nameIndexWriter;
    private final Clock clock;

    @Autowired
    public PlayerIndexCompactor(PlayerIndexProperties properties) {
        this(Path.of(properties.dir()), Clock.systemUTC());
    }

    PlayerIndexCompactor(Path dir, Clock clock) {
        this(dir, clock, PlayerNameIndexWriter.DEFAULT_CHUNK_LINES);
    }

    PlayerIndexCompactor(Path dir, Clock clock, int nameChunkLines) {
        this.inboxDir = dir.resolve("inbox");
        this.byTagDir = dir.resolve("by-tag");
        this.byNameDir = dir.resolve("by-name");
        this.nameRebuildMarker = dir.resolve("by-name.rebuild");
        this.workDir = dir.resolve("work-compact");
        this.nameIndexWriter = new PlayerNameIndexWriter(byNameDir, nameChunkLines);
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

    /**
     * by-nameが使えない状態(初回、以前の形式、前回の反映途中で落ちた)なら、定時を待たずに起動の1分後に作り直す。
     * 待つと、それまで名前検索が0件になるため。起動直後の混み合う時間を避けて1分置く。
     */
    @Scheduled(initialDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void rebuildNamesIfNeeded() {
        if (needsNameRebuild()) {
            compactOnSchedule();
        }
    }

    // by-nameが無い(初回、または目次の無い以前の形式)か、前回by-nameに反映し終える前に落ちた場合は、差分では直せない。
    private boolean needsNameRebuild() {
        return !Files.exists(byNameDir.resolve(PlayerNameIndex.INDEX_FILE)) || Files.exists(nameRebuildMarker);
    }

    synchronized void compact() throws IOException {
        List<Path> inbox = closedInboxFiles();
        boolean rebuildNames = needsNameRebuild();
        if (inbox.isEmpty() && (!rebuildNames || !Files.isDirectory(byTagDir))) {
            return;
        }
        long started = clock.millis();
        deleteRecursively(workDir);
        Files.createDirectories(byTagDir);
        Files.createDirectories(byNameDir);
        // by-tagを書き換えた後、by-nameに反映し終える前に落ちると、次回はby-tagに変化が無いので差分が出ない。
        // その場合に全件から作り直せるよう、反映し終えるまで印を残す。
        if (!Files.exists(nameRebuildMarker)) {
            Files.createFile(nameRebuildMarker);
        }

        ShardWriter tagSplit = new ShardWriter(workDir.resolve("tags"), TAG_SHARDS, TAG_FLUSH_CHARS);
        long lines = split(inbox, tagSplit);
        Path nameDiffFile = workDir.resolve("name-diffs.tsv");
        long players = 0;
        try (Writer nameDiffs = Files.newBufferedWriter(nameDiffFile, StandardCharsets.UTF_8)) {
            for (int shard = 0; shard < TAG_SHARDS; shard++) {
                players += merge(shard, tagSplit, rebuildNames ? null : nameDiffs);
            }
            if (rebuildNames) {
                addAllNames(nameDiffs);
            }
        }
        int nameFiles = nameIndexWriter.apply(nameDiffFile, workDir.resolve("name-sort"), rebuildNames,
                Long.toString(clock.millis()));
        Files.delete(nameRebuildMarker);

        for (Path file : inbox) {
            Files.delete(file);
        }
        deleteRecursively(workDir);
        log.info("player index compacted: {} inbox files, {} lines, {} players in updated by-tag files, "
                        + "{} by-name files {} ({} ms)",
                inbox.size(), lines, players, nameFiles, rebuildNames ? "rebuilt" : "updated",
                clock.millis() - started);
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

    private long split(List<Path> inbox, ShardWriter tagSplit) throws IOException {
        long lines = 0;
        for (Path file : inbox) {
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int tab = line.indexOf('\t');
                    if (tab <= 0) {
                        continue;
                    }
                    tagSplit.append(shardOf(line.substring(0, tab)), line);
                    lines++;
                }
            }
        }
        tagSplit.flush();
        return lines;
    }

    /**
     * by-tagの1ファイルに、振り分けたinboxの行を反映する。同じタグは最後に確認された名前を残す。反映後の件数を返す。
     * nameDiffsを渡すと、変わった行をby-nameへの差分として書き出す。
     */
    private int merge(int shard, ShardWriter tagSplit, Writer nameDiffs) throws IOException {
        Path target = byTagFile(shard);
        Path split = tagSplit.file(shard);
        if (!Files.exists(split)) {
            return 0;
        }
        Map<String, String[]> before = new HashMap<>();
        readByTagRows(target, row -> before.put(row[0], row));
        Map<String, String[]> latest = new HashMap<>(before);
        // 確認日時はUTC・秒単位のISO形式(例: 2026-09-18T10:15:30Z)で揃っているので、文字列の比較で新旧が分かる。
        readByTagRows(split, row -> latest.merge(row[0], row, (a, b) -> b[2].compareTo(a[2]) > 0 ? b : a));
        if (nameDiffs != null) {
            for (String[] row : latest.values()) {
                String[] old = before.get(row[0]);
                if (!Arrays.equals(old, row)) {
                    writeNameDiff(nameDiffs, old, row);
                }
            }
        }
        List<String[]> rows = new ArrayList<>(latest.values());
        rows.sort(Comparator.comparing(row -> row[0]));
        replace(target, rows.stream().map(row -> row[0] + "\t" + row[1] + "\t" + row[2]).toList());
        return rows.size();
    }

    /** 差分の形式は {@link PlayerNameIndexWriter#apply} を参照。 */
    private static void writeNameDiff(Writer nameDiffs, String[] old, String[] row) throws IOException {
        String key = PlayerNameIndex.normalize(row[1]);
        if (old != null) {
            String oldKey = PlayerNameIndex.normalize(old[1]);
            if (!oldKey.equals(key)) {
                nameDiffs.write(oldKey + "\t" + old[0] + "\t-\n");
            }
        }
        nameDiffs.write(key + "\t" + row[0] + "\t+\t" + row[1] + "\t" + row[2] + "\n");
    }

    private void addAllNames(Writer nameDiffs) throws IOException {
        for (int shard = 0; shard < TAG_SHARDS; shard++) {
            readByTagRows(byTagFile(shard), row -> writeNameDiff(nameDiffs, null, row));
        }
    }

    private Path byTagFile(int shard) {
        return byTagDir.resolve(String.format("%04d.tsv", shard));
    }

    private interface RowHandler {
        void accept(String[] row) throws IOException;
    }

    private static void readByTagRows(Path file, RowHandler handler) throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\t", -1);
                if (fields.length == 3 && !fields[0].isEmpty()) {
                    handler.accept(fields);
                }
            }
        }
    }

    // 書きかけのファイルを検索などが読まないよう、書き終えてから置き換える。
    private static void replace(Path target, List<String> lines) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            for (String line : lines) {
                writer.write(line);
                writer.write('\n');
            }
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * 振り分け先ごとにメモリに溜めてまとめて追記する。
     * 振り分け先のファイル(1024個)を同時に開くと、OSの上限(既定1024)に掛かり得るため。
     */
    private static final class ShardWriter {
        private final Path dir;
        private final StringBuilder[] buffers;
        private final int flushChars;

        ShardWriter(Path dir, int shards, int flushChars) throws IOException {
            this.dir = Files.createDirectories(dir);
            this.buffers = new StringBuilder[shards];
            this.flushChars = flushChars;
        }

        Path file(int shard) {
            return dir.resolve(shard + ".tsv");
        }

        void append(int shard, String line) throws IOException {
            if (buffers[shard] == null) {
                buffers[shard] = new StringBuilder();
            }
            buffers[shard].append(line).append('\n');
            if (buffers[shard].length() >= flushChars) {
                flush(shard);
            }
        }

        void flush() throws IOException {
            for (int shard = 0; shard < buffers.length; shard++) {
                flush(shard);
            }
        }

        private void flush(int shard) throws IOException {
            StringBuilder buffer = buffers[shard];
            if (buffer == null || buffer.isEmpty()) {
                return;
            }
            try (Writer writer = Files.newBufferedWriter(file(shard), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.append(buffer);
            }
            buffer.setLength(0);
        }
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
