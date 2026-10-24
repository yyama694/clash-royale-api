package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.client.exception.ClashRoyaleApiException;
import com.example.clashroyaleapi.domain.CurrentDeck;
import com.example.clashroyaleapi.domain.ProPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * プロ・クリエイターの一覧({@code pros/players.tsv})と、各人の今の順位・使用中のデッキ。
 * 1人につき公式APIをプロフィールと対戦履歴の2回呼ぶ。使用中のデッキはプロフィールの currentDeck だけでは
 * 対戦で使っているデッキと限らないため(CurrentDeck 参照)。
 * 全員分を取り直すと5秒ほどかかる(30人、2026-10-24にローカルで実測)ので、画面には直前に取った結果をすぐ返し、
 * 古くなっていたら裏で取り直す。待たせるのは起動後の最初の表示だけ。
 */
@Service
public class ProPlayerService {

    private static final Logger log = LoggerFactory.getLogger(ProPlayerService.class);

    private static final int MAX_CONCURRENT = 4;
    private static final Duration REFRESH_AFTER = Duration.ofMinutes(2);

    static final Comparator<Entry> ORDER = Comparator
            .comparing((Entry entry) -> !entry.available())
            .thenComparing(Entry::rank, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(entry -> entry.available() ? -entry.player().trophies() : 0);

    private final List<ProPlayer> players;
    private final Map<String, ProPlayer> byTag;
    private final PlayerService playerService;
    private final Clock clock;
    private final Executor refresher;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();
    private final AtomicBoolean refreshing = new AtomicBoolean();

    private record Snapshot(List<Entry> entries, Instant takenAt) {
    }

    @Autowired
    public ProPlayerService(PlayerService playerService) {
        this(ProPlayer.parse(lines("pros/players.tsv")), playerService, Clock.systemUTC(),
                task -> Thread.ofVirtual().start(task));
    }

    /** @param refresher 古くなった結果の取り直しを走らせる先 */
    ProPlayerService(List<ProPlayer> players, PlayerService playerService, Clock clock, Executor refresher) {
        this.players = players;
        this.byTag = players.stream().collect(Collectors.toUnmodifiableMap(ProPlayer::tag, Function.identity()));
        this.playerService = playerService;
        this.clock = clock;
        this.refresher = refresher;
    }

    /** 一覧に載せている人なら、その人。プレイヤー情報画面で肩書きを出すのに使う。 */
    public Optional<ProPlayer> find(String tag) {
        return Optional.ofNullable(byTag.get(Tags.normalize(tag)));
    }

    /**
     * @param player 取得できなければ null
     * @param deck   使用中のデッキ。求められなければ null
     */
    public record Entry(ProPlayer pro, PlayerResponse player, CurrentDeck deck) {

        public boolean available() {
            return player != null;
        }

        /** 今シーズンのランク戦の世界順位。順位が無ければ null。 */
        public Integer rank() {
            PlayerResponse.RankedSeasonResult ranked = player == null ? null : player.currentPathOfLegendSeasonResult();
            return ranked != null && ranked.hasRank() ? ranked.rank() : null;
        }
    }

    /**
     * 全員の状態を、ランク戦の世界順位の順に。順位の無い人はトロフィーの多い順に後ろへ、取得できなかった人は最後に並べる。
     * 前の結果が{@link #REFRESH_AFTER}(公式APIの応答のキャッシュと同じ2分)より古ければ、それを返しつつ裏で取り直す。
     */
    public List<Entry> current() {
        Snapshot last = snapshot.get();
        if (last == null) {
            return refresh();
        }
        if (Duration.between(last.takenAt(), clock.instant()).compareTo(REFRESH_AFTER) >= 0
                && refreshing.compareAndSet(false, true)) {
            refresher.execute(() -> {
                try {
                    refresh();
                } catch (RuntimeException e) {
                    log.warn("pro players refresh failed", e);
                } finally {
                    refreshing.set(false);
                }
            });
        }
        return last.entries();
    }

    /**
     * 取得できなかった人は、前の結果で取れていればそれを使う(一時的な失敗で、その人のデッキが消えないように)。
     */
    private List<Entry> refresh() {
        Semaphore semaphore = new Semaphore(MAX_CONCURRENT);
        List<Future<Entry>> futures;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            futures = players.stream().map(pro -> executor.submit(() -> lookup(pro, semaphore))).toList();
        }
        Snapshot last = snapshot.get();
        Map<String, Entry> previous = last == null ? Map.of() : last.entries().stream()
                .filter(Entry::available)
                .collect(Collectors.toMap(entry -> entry.pro().tag(), Function.identity()));
        List<Entry> entries = new ArrayList<>();
        for (Future<Entry> future : futures) {
            Entry entry = join(future);
            entries.add(entry.available() ? entry : previous.getOrDefault(entry.pro().tag(), entry));
        }
        entries.sort(ORDER);
        List<Entry> result = List.copyOf(entries);
        snapshot.set(new Snapshot(result, clock.instant()));
        return result;
    }

    private Entry lookup(ProPlayer pro, Semaphore semaphore) throws InterruptedException {
        semaphore.acquire();
        try {
            PlayerResponse player = playerService.findPlayer(pro.tag());
            // 対戦履歴が取れなくても、順位とプロフィールの currentDeck は出す。
            List<BattleLogEntry> battleLog;
            try {
                battleLog = playerService.findBattleLog(pro.tag());
            } catch (ClashRoyaleApiException e) {
                battleLog = List.of();
            }
            return new Entry(pro, player, playerService.currentDeckOf(player, battleLog).orElse(null));
        } catch (ClashRoyaleApiException e) {
            return new Entry(pro, null, null);
        } finally {
            semaphore.release();
        }
    }

    private static <T> T join(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    private static List<String> lines(String resource) {
        try (InputStream in = ProPlayerService.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing resource: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
