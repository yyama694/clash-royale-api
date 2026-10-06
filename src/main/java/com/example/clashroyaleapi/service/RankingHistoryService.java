package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.config.PlayerIndexProperties;
import com.example.clashroyaleapi.domain.LeaderHistory;
import com.example.clashroyaleapi.domain.RankingMovements;
import com.example.clashroyaleapi.domain.RankingSnapshot;
import com.example.clashroyaleapi.store.RankingSnapshotStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * ランキングの記録(RankingRecorderが定期的に取る)を持ち、ランキングの動き画面の中身を組み立てる。
 * 日付の区切りはUTC(世界中の利用者向けなので、特定の国の時刻に寄せない)。
 */
@Service
public class RankingHistoryService {

    private static final Logger log = LoggerFactory.getLogger(RankingHistoryService.class);

    public static final int TOP_SIZE = 10;
    public static final int CLIMBER_LIMIT = 10;
    public static final int LEADER_CHANGE_LIMIT = 10;

    /** 最新の動きは、この時間だけ前の記録と比べる(シーズンの最初の記録がそれより新しければ、最初の記録と)。 */
    public static final Duration LATEST_WINDOW = Duration.ofHours(24);
    /** 最新の動きの画面に出す首位交代の期間。 */
    public static final Duration LATEST_CHANGES_WINDOW = Duration.ofDays(7);
    /** 最新の記録がこれより古ければ、記録が止まっていると断る(15分ごとの記録を4回続けて取れていない)。 */
    static final Duration STALE_AFTER = Duration.ofHours(1);

    // 1件=1000人分。最新の画面は最新と24時間前の2件を、日ごとの画面はその日と前の日の終わりの2件を読む。
    private static final int CACHED_SNAPSHOTS = 16;

    /**
     * @param day             日ごとの動きならその日(UTC)、最新の動きなら null
     * @param leaderChanges   新しい順。最新の動きは直近7日分、日ごとの動きはその日の分
     * @param reign           after の1位がいつから1位か
     */
    public record Report(LocalDate day, RankingMovements movements, List<LeaderHistory.Change> leaderChanges,
            LeaderHistory.Reign reign) {
    }

    private final RankingSnapshotStore store;
    private final Clock clock;
    private volatile List<RankingSnapshotStore.Summary> summaries;
    private final Map<Instant, RankingSnapshot> cache = new LinkedHashMap<>(CACHED_SNAPSHOTS, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Instant, RankingSnapshot> eldest) {
            return size() > CACHED_SNAPSHOTS;
        }
    };

    @Autowired
    public RankingHistoryService(PlayerIndexProperties properties) {
        // プレイヤー名の索引と同じく、jarを差し替えても消えない場所に置く。
        this(new RankingSnapshotStore(Path.of(properties.dir()).resolve("ranking-history").resolve("global")),
                Clock.systemUTC());
    }

    RankingHistoryService(RankingSnapshotStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
        List<RankingSnapshotStore.Summary> loaded;
        try {
            loaded = store.summaries();
        } catch (IOException e) {
            log.warn("could not read the ranking history (only new records will be shown): {}", e.toString());
            loaded = List.of();
        }
        this.summaries = List.copyOf(loaded);
    }

    /** 記録を足す。呼ぶのは記録のバッチ(スケジューラーのスレッド1本)だけ。 */
    public void record(RankingSnapshot snapshot) throws IOException {
        if (snapshot.entries().isEmpty()) {
            return;
        }
        store.write(snapshot);
        List<RankingSnapshotStore.Summary> next = new ArrayList<>(summaries);
        next.add(new RankingSnapshotStore.Summary(snapshot.takenAt(), snapshot.finishedSeason(),
                snapshot.entries().getFirst()));
        next.sort(Comparator.comparing(RankingSnapshotStore.Summary::takenAt));
        summaries = List.copyOf(next);
        synchronized (cache) {
            cache.put(snapshot.takenAt(), snapshot);
        }
    }

    /** 最新の記録と、その24時間前からの動き。まだ記録が無ければ空。 */
    public Optional<Report> latest() {
        List<RankingSnapshotStore.Summary> all = summaries;
        if (all.isEmpty()) {
            return Optional.empty();
        }
        RankingSnapshotStore.Summary last = all.getLast();
        Instant windowStart = last.takenAt().minus(LATEST_WINDOW);
        Optional<RankingSnapshotStore.Summary> base = all.stream()
                .filter(summary -> sameSeason(summary, last))
                .filter(summary -> !summary.takenAt().isBefore(windowStart))
                .filter(summary -> summary.takenAt().isBefore(last.takenAt()))
                .findFirst();
        return report(null, all, last, base, last.takenAt().minus(LATEST_CHANGES_WINDOW));
    }

    /**
     * その日(UTC)の動き。前の日の最後の記録(シーズンが同じなら)と、その日の最後の記録を比べる。
     * まだ終わっていない日と、記録の無い日は空。
     */
    public Optional<Report> day(LocalDate day) {
        if (!day.isBefore(today())) {
            return Optional.empty();
        }
        Instant start = day.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<RankingSnapshotStore.Summary> all = summaries;
        Optional<RankingSnapshotStore.Summary> last = all.stream()
                .filter(summary -> !summary.takenAt().isBefore(start) && summary.takenAt().isBefore(end))
                .reduce((first, second) -> second);
        if (last.isEmpty()) {
            return Optional.empty();
        }
        List<RankingSnapshotStore.Summary> season = all.stream().filter(summary -> sameSeason(summary, last.get()))
                .toList();
        Optional<RankingSnapshotStore.Summary> base = season.stream()
                .filter(summary -> summary.takenAt().isBefore(start))
                .reduce((first, second) -> second)
                .or(() -> season.stream()
                        .filter(summary -> !summary.takenAt().isBefore(start))
                        .filter(summary -> summary.takenAt().isBefore(last.get().takenAt()))
                        .findFirst());
        return report(day, all, last.get(), base, start);
    }

    /**
     * 最新の動きのもとの記録が古いままか。シーズンが替わった直後は今シーズンのランキングが空で記録しないので、
     * 新しいシーズンに人が入るまで(2026-10-05は1日以上)、前のシーズンの最後の記録が最新のまま残る。
     */
    public boolean isStale(Report report) {
        return report.day() == null
                && clock.instant().isAfter(report.movements().after().takenAt().plus(STALE_AFTER));
    }

    /** 記録のある、終わった日(UTC)を新しい順に。 */
    public List<LocalDate> archiveDays() {
        LocalDate today = today();
        return summaries.stream()
                .map(summary -> LocalDate.ofInstant(summary.takenAt(), ZoneOffset.UTC))
                .filter(day -> day.isBefore(today))
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    private Optional<Report> report(LocalDate day, List<RankingSnapshotStore.Summary> all,
            RankingSnapshotStore.Summary last, Optional<RankingSnapshotStore.Summary> base, Instant changesFrom) {
        Optional<RankingSnapshot> after = snapshot(last.takenAt());
        if (after.isEmpty()) {
            return Optional.empty();
        }
        RankingSnapshot before = base.flatMap(summary -> snapshot(summary.takenAt())).orElse(null);
        LeaderHistory history = new LeaderHistory(all.stream()
                .filter(summary -> sameSeason(summary, last))
                .filter(summary -> !summary.takenAt().isAfter(last.takenAt()))
                .map(summary -> new LeaderHistory.Point(summary.takenAt(), summary.finishedSeason(), summary.leader()))
                .toList());
        List<LeaderHistory.Change> changes = history.changes(changesFrom, last.takenAt());
        if (day == null) {
            changes = changes.stream().limit(LEADER_CHANGE_LIMIT).toList();
        }
        return Optional.of(new Report(day, RankingMovements.between(before, after.get(), TOP_SIZE, CLIMBER_LIMIT),
                changes, history.reignAt(last.takenAt()).orElse(null)));
    }

    private Optional<RankingSnapshot> snapshot(Instant takenAt) {
        synchronized (cache) {
            RankingSnapshot cached = cache.get(takenAt);
            if (cached != null) {
                return Optional.of(cached);
            }
        }
        // ディスクを読む間はロックを持たない(リクエストのスレッドを詰まらせないため)。
        try {
            Optional<RankingSnapshot> read = store.read(takenAt);
            read.ifPresent(snapshot -> {
                synchronized (cache) {
                    cache.put(takenAt, snapshot);
                }
            });
            return read;
        } catch (IOException e) {
            log.warn("could not read the ranking record at {}: {}", takenAt, e.toString());
            return Optional.empty();
        }
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static boolean sameSeason(RankingSnapshotStore.Summary a, RankingSnapshotStore.Summary b) {
        return Objects.equals(a.finishedSeason(), b.finishedSeason());
    }
}
