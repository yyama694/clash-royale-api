package com.example.clashroyaleapi.store;

import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.config.PlayerIndexProperties;
import com.example.clashroyaleapi.domain.PlayerSighting;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 見かけたプレイヤーを inbox/ に1時間ごとのTSVとして追記する(形式: tag \t name \t 確認日時)。
 * 名前検索用の索引(by-tag/by-name)への整理は後段のバッチで行う前提で、ここでは追記だけにする。
 * 詳細は「プレイヤー名検索（検討中）.md」を参照。
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

    @Autowired
    public PlayerSightingLog(PlayerIndexProperties properties) {
        this(Path.of(properties.dir()).resolve("inbox"), Clock.systemUTC());
    }

    PlayerSightingLog(Path inboxDir, Clock clock) {
        this.inboxDir = inboxDir;
        this.clock = clock;
    }

    /** 蓄積は付加的な機能なので、書き込みに失敗しても画面表示を妨げないよう例外は投げずにログだけ残す。 */
    public synchronized void record(Collection<PlayerSighting> sightings) {
        Set<String> keys = new LinkedHashSet<>();
        for (PlayerSighting sighting : sightings) {
            if (sighting.tag() == null || sighting.tag().isBlank()
                    || sighting.name() == null || sighting.name().isBlank()) {
                continue;
            }
            String key = Tags.normalize(sighting.tag()) + "\t" + sanitize(sighting.name());
            if (recent.getIfPresent(key) == null) {
                keys.add(key);
            }
        }
        if (keys.isEmpty()) {
            return;
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        try {
            Files.createDirectories(inboxDir);
            try (Writer writer = Files.newBufferedWriter(inboxDir.resolve(FILE_NAME.format(now)),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                for (String key : keys) {
                    writer.write(key + "\t" + now + "\n");
                }
            }
            // 書けたものだけ「最近書いた」扱いにする(失敗したものは次に見かけたときに再度書く)。
            keys.forEach(key -> recent.put(key, Boolean.TRUE));
        } catch (IOException e) {
            log.warn("failed to record {} player sightings: {}", keys.size(), e.toString());
        }
    }

    /** 区切り文字(タブ・改行)が名前に含まれていると行が壊れるため、空白に置き換える。 */
    private static String sanitize(String name) {
        return name.replaceAll("[\\t\\r\\n]", " ");
    }
}
