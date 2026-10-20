package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.config.PlayerIndexProperties;
import com.example.clashroyaleapi.domain.CardUsage;
import com.example.clashroyaleapi.domain.TopDecks;
import com.example.clashroyaleapi.store.TopDecksFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 最新の集計結果(世界トップのデッキと、そこから求めたカード使用率)を持つ。集計はTopDeckCollectorが行い、ここに渡す。
 * 画面からは読むだけなので、差し替えは参照の付け替え1回で済ませる。
 */
@Service
public class CardUsageService {

    private static final Logger log = LoggerFactory.getLogger(CardUsageService.class);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss").withZone(ZoneOffset.UTC);

    static final Duration WEEK_AGO = Duration.ofDays(7).minusHours(12);

    private final TopDecksFile file;
    private final Path historyDir;
    private volatile TopDecks topDecks;
    private volatile CardUsage current;
    private final Map<Instant, Optional<CardUsage>> before = new ConcurrentHashMap<>();
    private final Map<LocalDate, CardUsage> firstOfDay = new ConcurrentHashMap<>();

    @Autowired
    public CardUsageService(PlayerIndexProperties properties) {
        // プレイヤー名の索引と同じく、jarを差し替えても消えない場所に置く。
        this(new TopDecksFile(Path.of(properties.dir()).resolve("card-usage").resolve("top-decks.tsv")),
                Path.of(properties.dir()).resolve("card-usage").resolve("history"));
    }

    CardUsageService(TopDecksFile file, Path historyDir) {
        this.file = file;
        this.historyDir = historyDir;
        try {
            file.read().ifPresent(this::replace);
        } catch (IOException e) {
            log.warn("could not read the last card usage snapshot (will be collected again): {}", e.toString());
        }
    }

    /** まだ一度も集計していなければ空。 */
    public Optional<CardUsage> current() {
        return Optional.ofNullable(current);
    }

    /** まだ一度も集計していなければ空。 */
    public Optional<TopDecks> topDecks() {
        return Optional.ofNullable(topDecks);
    }

    public void publish(TopDecks topDecks) {
        replace(topDecks);
        try {
            file.write(topDecks);
        } catch (IOException e) {
            // 画面には新しい集計が出ているので、保存の失敗は次回の再起動まで影響しない。
            log.warn("could not save the card usage snapshot: {}", e.toString());
        }
        // 公式APIは直近約30戦しか返さず、過去のデッキは後から集め直せない。使用率の推移を出せるよう、毎回の集計を残す。
        try {
            historyFile(topDecks).write(topDecks);
        } catch (IOException e) {
            log.warn("could not save the card usage history: {}", e.toString());
        }
    }

    /**
     * 使用率ランキングの増減を比べる、約1週間前の集計。最新の集計より6日半以上前のうち最後のもの。
     * 集計は約25時間おきに時刻がずれていくので、ちょうど7日前の集計は無い。履歴が1週間分たまるまでは空。
     */
    public Optional<CardUsage> weekAgo() {
        CardUsage latest = current;
        return latest == null ? Optional.empty() : lastBefore(latest.collectedAt().minus(WEEK_AGO));
    }

    /**
     * その時刻より前の最後の集計(バランス調整の前の使用率に使う)。履歴が無ければ空。
     * 調整ごとに1回読めば済むので、読んだ結果を覚えておく。
     */
    public Optional<CardUsage> lastBefore(Instant time) {
        return before.computeIfAbsent(time, this::readLastBefore);
    }

    private Optional<CardUsage> readLastBefore(Instant time) {
        String cutoff = DAY.format(time) + "/" + TIME.format(time) + ".tsv";
        try (Stream<Path> files = Files.exists(historyDir) ? Files.walk(historyDir, 2) : Stream.empty()) {
            Optional<Path> last = files.filter(Files::isRegularFile)
                    .filter(file -> relative(file).compareTo(cutoff) < 0)
                    .max(Comparator.comparing(this::relative));
            if (last.isEmpty()) {
                return Optional.empty();
            }
            return new TopDecksFile(last.get()).read().map(CardUsage::of);
        } catch (IOException e) {
            log.warn("could not read the card usage history before {}: {}", time, e.toString());
            return Optional.empty();
        }
    }

    /**
     * その日(UTC)の最初の集計(バランス調整から何日後かの使用率に使う)。無ければ空。
     * その日の集計は後から書かれうるので、見つかったときだけ覚えておく。
     */
    public Optional<CardUsage> firstOn(LocalDate day) {
        CardUsage cached = firstOfDay.get(day);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<CardUsage> read = readFirstOn(day);
        read.ifPresent(usage -> firstOfDay.put(day, usage));
        return read;
    }

    private Optional<CardUsage> readFirstOn(LocalDate day) {
        Path dir = historyDir.resolve(day.toString());
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(dir)) {
            Optional<Path> first = files.filter(Files::isRegularFile)
                    .min(Comparator.comparing(file -> file.getFileName().toString()));
            if (first.isEmpty()) {
                return Optional.empty();
            }
            return new TopDecksFile(first.get()).read().map(CardUsage::of);
        } catch (IOException e) {
            log.warn("could not read the card usage history of {}: {}", day, e.toString());
            return Optional.empty();
        }
    }

    private String relative(Path file) {
        return historyDir.relativize(file).toString().replace('\\', '/');
    }

    /** 集計ごとに history/<日付>/<時分秒>.tsv(UTC)。中身は top-decks.tsv と同じ形式。 */
    private TopDecksFile historyFile(TopDecks topDecks) {
        Instant collectedAt = topDecks.collectedAt();
        return new TopDecksFile(historyDir.resolve(DAY.format(collectedAt)).resolve(TIME.format(collectedAt) + ".tsv"));
    }

    private void replace(TopDecks latest) {
        CardUsage usage = CardUsage.of(latest);
        topDecks = latest;
        current = usage;
    }
}
