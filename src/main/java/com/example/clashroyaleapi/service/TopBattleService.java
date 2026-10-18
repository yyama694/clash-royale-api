package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.config.CardUsageProperties;
import com.example.clashroyaleapi.config.PlayerIndexProperties;
import com.example.clashroyaleapi.domain.CardMatchups;
import com.example.clashroyaleapi.domain.ElixirLeak;
import com.example.clashroyaleapi.domain.TopBattle;
import com.example.clashroyaleapi.store.TopBattleStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 世界上位どうしの直近1週間のランク戦と、そこから求めた集計(カードの相性・消費漏れ)を持つ。
 * 対戦はデッキ集計(TopDeckCollector)が毎日の集計のついでに集め、ここに渡す。画面からは読むだけ。
 */
@Service
public class TopBattleService {

    private static final Logger log = LoggerFactory.getLogger(TopBattleService.class);

    // バランス調整やメタの変化が早く反映されるよう短めにする。上位1000人どうしで約1万戦になる見込み。
    static final Duration WINDOW = Duration.ofDays(7);

    private final TopBattleStore store;
    private final Clock clock;
    private final int players;
    private volatile Snapshot snapshot = new Snapshot(List.of());
    private volatile boolean published;

    private record Snapshot(List<TopBattle> battles, CardMatchups matchups, ElixirLeak leak) {

        Snapshot(List<TopBattle> battles) {
            this(battles, CardMatchups.of(battles), ElixirLeak.ofTopBattles(battles));
        }
    }

    @Autowired
    public TopBattleService(PlayerIndexProperties indexProperties, CardUsageProperties usageProperties) {
        // プレイヤー名の索引と同じく、jarを差し替えても消えない場所に置く。
        this(new TopBattleStore(Path.of(indexProperties.dir()).resolve("card-usage").resolve("top-battles")),
                Clock.systemUTC(), usageProperties.players());
    }

    TopBattleService(TopBattleStore store, Clock clock, int players) {
        this.store = store;
        this.clock = clock;
        this.players = players;
        try {
            snapshot = new Snapshot(store.readSince(clock.instant().minus(WINDOW)));
        } catch (IOException e) {
            log.warn("could not read the top battles (will be collected again): {}", e.toString());
        }
    }

    /** 集めた対戦を足し、1週間より前の対戦を集計から外す。 */
    public void publish(Collection<TopBattle> collected) {
        Instant since = clock.instant().minus(WINDOW);
        Map<String, TopBattle> merged = new LinkedHashMap<>();
        for (TopBattle battle : snapshot.battles()) {
            merged.put(battle.key(), battle);
        }
        for (TopBattle battle : collected) {
            merged.put(battle.key(), battle);
        }
        snapshot = new Snapshot(merged.values().stream().filter(battle -> !battle.time().isBefore(since)).toList());
        published = true;
        try {
            store.add(collected);
        } catch (IOException e) {
            // 画面には新しい集計が出ているので、保存の失敗は次回の再起動まで影響しない。
            log.warn("could not save the top battles: {}", e.toString());
        }
    }

    /**
     * 直近1週間の対戦が無く、起動してから一度も集めていなければ true(対戦を集め始める前の、デッキだけの集計しか無いときなど)。
     * 集めても0戦だったとき(公式APIが消費漏れを返さなくなったときなど)に、集計を休みなく繰り返さないよう、起動後の1回だけにする。
     */
    public boolean awaitingFirstCollection() {
        return !published && snapshot.battles().isEmpty();
    }

    /** 対戦を集めている世界ランキングの人数(上位何人どうしの対戦か)。 */
    public int players() {
        return players;
    }

    /** 直近1週間の対戦が無ければ空。 */
    public Optional<CardMatchups> matchups() {
        Snapshot current = snapshot;
        return current.battles().isEmpty() ? Optional.empty() : Optional.of(current.matchups());
    }

    /** 直近1週間の対戦が無ければ空。 */
    public Optional<ElixirLeak> elixirLeak() {
        Snapshot current = snapshot;
        return current.battles().isEmpty() ? Optional.empty() : Optional.of(current.leak());
    }
}
