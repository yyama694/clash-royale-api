package com.example.clashroyaleapi.store;

import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.config.PlayerIndexProperties;
import com.example.clashroyaleapi.domain.PlayerSighting;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 見かけたプレイヤーを inbox/ に1時間ごとのTSVとして追記する(形式: tag \t name \t 確認日時)。
 * 名前検索用の索引(by-tag/by-name)への整理は後段のバッチで行う前提で、ここでは追記だけにする。
 * 詳細は「プレイヤー名検索（検討中）.md」を参照。
 *
 * record/recordCrawledはリクエストスレッドから同期的に呼ばれるため、ここではキューに積むだけにして
 * ディスクI/Oはしない。実際の書き込みは{@link #flush()}でまとめて行う。
 * (2026-09-20、AIクローラーの高頻度アクセスで、旧実装の書き込み用ロックが詰まりTomcatのスレッドプールが
 * 枯渇してサイト全体が応答不能になった障害の対策。詳細は進捗ログ.mdフェーズ1.49)
 */
@Component
public class PlayerSightingLog {

    private static final Logger log = LoggerFactory.getLogger(PlayerSightingLog.class);

    private static final DateTimeFormatter FILE_NAME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HH'.tsv'").withZone(ZoneOffset.UTC);

    // ランキングは画面を開くたびに同じ1000人を返すため、最近書いた(タグ, 名前)の組は書かない。
    // 忘れて重複して書いても後段の整理で吸収できるので、メモリを優先して上限を小さめにしている。
    private static final int RECENT_CAPACITY = 50_000;
    private static final Duration RECENT_TTL = Duration.ofDays(1);

    private final Path inboxDir;
    private final Clock clock;
    private final Cache<String, Boolean> recent = Caffeine.newBuilder()
            .maximumSize(RECENT_CAPACITY)
            .expireAfterWrite(RECENT_TTL)
            .build();
    private final ConcurrentLinkedQueue<Entry> pending = new ConcurrentLinkedQueue<>();

    @Autowired
    public PlayerSightingLog(PlayerIndexProperties properties) {
        this(Path.of(properties.dir()).resolve("inbox"), Clock.systemUTC());
    }

    PlayerSightingLog(Path inboxDir, Clock clock) {
        this.inboxDir = inboxDir;
        this.clock = clock;
    }

    /** キューに積むだけで即座に返る(ディスクI/Oはしない)。「最近書いた」判定はここで即時に行う。 */
    public void record(Collection<PlayerSighting> sightings) {
        Set<String> keys = keysOf(sightings);
        keys.removeIf(key -> recent.getIfPresent(key) != null);
        keys.forEach(key -> {
            recent.put(key, Boolean.TRUE);
            pending.add(new Entry("", key));
        });
    }

    /**
     * 巡回で集めた分を crawl-yyyyMMdd-HH.tsv に書く。巡回は件数が桁違いに多く、「最近書いた組」を使うと
     * 閲覧由来の分がすぐ追い出されて役に立たなくなるため、ここでは重複を除かず後段の整理に任せる。
     */
    public void recordCrawled(Collection<PlayerSighting> sightings) {
        keysOf(sightings).forEach(key -> pending.add(new Entry("crawl-", key)));
    }

    /** 蓄積は付加的な機能なので、書き込みに失敗しても画面表示を妨げないよう例外は投げずにログだけ残す。 */
    @Scheduled(fixedDelay = 2000)
    public void flush() {
        Entry entry = pending.poll();
        if (entry == null) {
            return;
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        String suffix = FILE_NAME.format(now);
        Map<String, List<String>> byFile = new LinkedHashMap<>();
        do {
            byFile.computeIfAbsent(entry.filePrefix() + suffix, key -> new ArrayList<>())
                    .add(entry.key() + "\t" + now);
        } while ((entry = pending.poll()) != null);
        byFile.forEach(this::append);
    }

    private void append(String fileName, List<String> lines) {
        try {
            Files.createDirectories(inboxDir);
            try (Writer writer = Files.newBufferedWriter(inboxDir.resolve(fileName),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                for (String line : lines) {
                    writer.write(line);
                    writer.write("\n");
                }
            }
        } catch (IOException e) {
            log.warn("failed to record {} player sightings to {}: {}", lines.size(), fileName, e.toString());
        }
    }

    private static Set<String> keysOf(Collection<PlayerSighting> sightings) {
        Set<String> keys = new LinkedHashSet<>();
        for (PlayerSighting sighting : sightings) {
            if (sighting.tag() == null || sighting.tag().isBlank()
                    || sighting.name() == null || sighting.name().isBlank()) {
                continue;
            }
            keys.add(Tags.normalize(sighting.tag()) + "\t" + sanitize(sighting.name()));
        }
        return keys;
    }

    /** 区切り文字(タブ・改行)が名前に含まれていると行が壊れるため、空白に置き換える。 */
    private static String sanitize(String name) {
        return name.replaceAll("[\\t\\r\\n]", " ");
    }

    private record Entry(String filePrefix, String key) {
    }
}
