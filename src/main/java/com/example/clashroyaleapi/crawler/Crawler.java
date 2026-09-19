package com.example.clashroyaleapi.crawler;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.ClanRankingResponse;
import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;
import com.example.clashroyaleapi.client.dto.RiverRaceLogResponse;
import com.example.clashroyaleapi.client.exception.ApiAccessDeniedException;
import com.example.clashroyaleapi.client.exception.ApiRateLimitException;
import com.example.clashroyaleapi.client.exception.ClashRoyaleApiException;
import com.example.clashroyaleapi.client.exception.ResourceNotFoundException;
import com.example.clashroyaleapi.config.CrawlerProperties;
import com.example.clashroyaleapi.domain.PlayerSighting;
import com.example.clashroyaleapi.store.PlayerSightingLog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * 名前検索用のプレイヤーを集める巡回(「プレイヤー名検索（検討中）.md」参照)。2種類を決まった割合で交互に行う。
 * <ul>
 *   <li>クラン対戦の履歴 … 1回で約240人(5クラン分の参加者。元メンバーを含む)と、次に回すクランが得られる。主力</li>
 *   <li>プレイヤーの対戦履歴 … 1回で約30人と効率は落ちるが、クランに入っていない人(実測で相手の約3割)を拾える</li>
 * </ul>
 */
class Crawler {

    private static final Logger log = LoggerFactory.getLogger(Crawler.class);

    private static final int SEED_RANKING_SIZE = 1000;
    private static final int SUMMARY_EVERY = 100;
    private static final Duration RATE_LIMIT_PAUSE = Duration.ofMinutes(1);
    private static final Duration MAX_RATE_LIMIT_PAUSE = Duration.ofMinutes(30);
    private static final Duration FAILURE_PAUSE = Duration.ofMinutes(1);
    // 403はIPアドレスの変化などで、待っても直らないことが多い。ログを溢れさせないよう長めに止める。
    private static final Duration ACCESS_DENIED_PAUSE = Duration.ofMinutes(30);

    private final ClashRoyaleApiClient apiClient;
    private final PlayerSightingLog sightingLog;
    private final TagQueue clanQueue;
    private final TagQueue playerQueue;
    private final CrawlerProperties properties;
    private final Clock clock;

    private Instant pausedUntil = Instant.MIN;
    private Duration rateLimitPause = RATE_LIMIT_PAUSE;
    private long turns;
    private long clansCrawled;
    private long playersCrawled;
    private long sightings;

    Crawler(ClashRoyaleApiClient apiClient, PlayerSightingLog sightingLog, TagQueue clanQueue,
            TagQueue playerQueue, CrawlerProperties properties, Clock clock) {
        this.apiClient = apiClient;
        this.sightingLog = sightingLog;
        this.clanQueue = clanQueue;
        this.playerQueue = playerQueue;
        this.properties = properties;
        this.clock = clock;
    }

    /** APIを1回呼ぶ分だけ進める。一定間隔で呼ばれる前提。 */
    void crawlNext() {
        if (clock.instant().isBefore(pausedUntil)) {
            return;
        }
        boolean battleLogTurn = (turns + 1) % properties.battleLogEvery() == 0;
        try {
            if (battleLogTurn) {
                step(playerQueue, this::playerSeeds, this::crawlPlayer);
            } else {
                step(clanQueue, this::clanSeeds, this::crawlClan);
            }
            // 429などで止まった回は数えず、同じ種類をやり直す(割合を崩さないため)。
            turns++;
            rateLimitPause = RATE_LIMIT_PAUSE;
            if (turns % SUMMARY_EVERY == 0) {
                log.info("crawler: {} clans and {} players crawled, {} player sightings",
                        clansCrawled, playersCrawled, sightings);
            }
        } catch (ApiRateLimitException e) {
            pause(rateLimitPause, "rate limited");
            rateLimitPause = min(rateLimitPause.multipliedBy(2), MAX_RATE_LIMIT_PAUSE);
        } catch (ApiAccessDeniedException e) {
            pause(ACCESS_DENIED_PAUSE, "access denied (check the allowed IP addresses of the API key)");
        } catch (ClashRoyaleApiException | IOException e) {
            pause(FAILURE_PAUSE, e.toString());
        }
    }

    private void step(TagQueue queue, Supplier<List<String>> seeds, TagCrawl crawl) throws IOException {
        Optional<String> next = queue.peek();
        if (next.isEmpty()) {
            queue.discover(seeds.get());
            return;
        }
        try {
            crawl.crawl(next.get());
        } catch (ResourceNotFoundException e) {
            // 解散したクランなど。回し続けても意味が無いので処理済みにする。
        }
        queue.done();
    }

    private void crawlClan(String clanTag) throws IOException {
        RiverRaceLogResponse response = apiClient.getRiverRaceLog(clanTag, properties.racesPerClan());
        List<PlayerSighting> found = new ArrayList<>();
        Set<String> clans = new LinkedHashSet<>();
        Set<String> players = new LinkedHashSet<>();
        for (RiverRaceLogResponse.RiverRace race : response.items()) {
            if (race.standings() == null) {
                continue;
            }
            for (RiverRaceLogResponse.Standing standing : race.standings()) {
                RiverRaceLogResponse.Clan clan = standing.clan();
                if (clan == null) {
                    continue;
                }
                if (clan.tag() != null && !Tags.normalize(clan.tag()).equals(Tags.normalize(clanTag))) {
                    clans.add(clan.tag());
                }
                if (clan.participants() == null || clan.participants().isEmpty()) {
                    continue;
                }
                clan.participants().forEach(p -> found.add(new PlayerSighting(p.tag(), p.name())));
                // 対戦履歴の巡回の起点を、ランキング上位以外の層にも広げるため、各クランから1人ずつ回す。
                // 対戦相手は近い強さの人に偏るので、上位から辿るだけでは初心者層に届かない。
                players.add(clan.participants().get(0).tag());
            }
        }
        sightingLog.recordCrawled(found);
        clanQueue.discover(clans);
        playerQueue.discover(players);
        clansCrawled++;
        sightings += found.size();
        log.debug("crawled clan {}: {} players, {} clans", clanTag, found.size(), clans.size());
    }

    private void crawlPlayer(String playerTag) throws IOException {
        List<PlayerSighting> found = new ArrayList<>();
        Set<String> others = new LinkedHashSet<>();
        for (BattleLogEntry battle : apiClient.getBattleLogUncached(playerTag)) {
            Stream.of(battle.team(), battle.opponent())
                    .filter(participants -> participants != null)
                    .flatMap(List::stream)
                    .forEach(p -> {
                        found.add(new PlayerSighting(p.tag(), p.name()));
                        if (p.tag() != null && !Tags.normalize(p.tag()).equals(Tags.normalize(playerTag))) {
                            others.add(p.tag());
                        }
                    });
        }
        sightingLog.recordCrawled(found);
        playerQueue.discover(others);
        playersCrawled++;
        sightings += found.size();
        log.debug("crawled player {}: {} players", playerTag, found.size());
    }

    // 回すクランが1つも無いとき(初回)は、グローバルのクランランキングを起点にする。
    private List<String> clanSeeds() {
        List<String> tags = apiClient.getClanRankings("global", SEED_RANKING_SIZE).stream()
                .map(ClanRankingResponse.RankedClan::tag)
                .toList();
        log.info("crawler: seeded {} clans from the global clan ranking", tags.size());
        return tags;
    }

    private List<String> playerSeeds() {
        List<String> tags = apiClient.getPathOfLegendRankings("global", SEED_RANKING_SIZE).stream()
                .map(PlayerRankingResponse.RankedPlayer::tag)
                .toList();
        log.info("crawler: seeded {} players from the global player ranking", tags.size());
        return tags;
    }

    private void pause(Duration duration, String reason) {
        pausedUntil = clock.instant().plus(duration);
        log.warn("crawler paused for {}: {}", duration, reason);
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    @FunctionalInterface
    private interface TagCrawl {
        void crawl(String tag) throws IOException;
    }
}
