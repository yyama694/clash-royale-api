package com.example.clashroyaleapi.crawler;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;
import com.example.clashroyaleapi.client.exception.ApiAccessDeniedException;
import com.example.clashroyaleapi.client.exception.ClashRoyaleApiException;
import com.example.clashroyaleapi.client.exception.ResourceNotFoundException;
import com.example.clashroyaleapi.config.CardUsageProperties;
import com.example.clashroyaleapi.domain.CardForm;
import com.example.clashroyaleapi.domain.CardLevel;
import com.example.clashroyaleapi.domain.GameText;
import com.example.clashroyaleapi.domain.SeasonCalendar;
import com.example.clashroyaleapi.domain.TopBattle;
import com.example.clashroyaleapi.domain.TopDecks;
import com.example.clashroyaleapi.service.CardUsageService;
import com.example.clashroyaleapi.service.TopBattleService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ランク戦の世界ランキング上位のプレイヤーの対戦履歴を1人ずつ取得し、直近のランク戦で使ったデッキを集める。
 * 全員分が揃ったら {@link CardUsageService} に渡し、次は refreshEvery 後に集め直す。
 *
 * プレイヤー情報の currentDeck(今セットしているデッキ)は使わない。上位60人で確かめたところ、
 * 47人は直前に遊んだ2v2などのデッキがセットされていてランク戦のデッキと違い、19人はチャンピオンが抜けた7枚で返ってきた。
 *
 * 全員を1回の処理で取得すると、スケジューラーのスレッド(巡回・索引の整理と共用、1本)を1時間近く占有してしまう。
 * そのため巡回と同じく、一定間隔で呼ばれるたびに1人分だけ進める。途中で再起動した場合は最初からやり直す。
 *
 * シーズン開始直後は今シーズンのランキングに数人しかおらず、そのまま集めると数人分の集計になる(2026-10-06に1人分になった)。
 * シーズン開始から3日以内で今シーズンのランキングが目標の人数に満たなければ、前のシーズンの最終順位の上位で集める
 * (2026-10-07にユーザーが決定。期間は {@link SeasonCalendar#NEW_SEASON_PERIOD})。
 *
 * 同じ対戦履歴から、相手も集める対象の人だった1対1のランク戦を {@link TopBattleService} に渡す(カードの相性・消費漏れ)。
 * 相手が対象外の対戦まで数えると、勝ち越している上位の人がよく使うカードほど勝率が高く出る
 * (上位100人の対戦で確かめたところ、本人たちの勝率は67%だった。2026-10-18)。
 */
class TopDeckCollector {

    private static final Logger log = LoggerFactory.getLogger(TopDeckCollector.class);

    private static final int DECK_SIZE = 8;
    private static final String RANKED_BATTLE_TYPE = "pathOfLegend";
    private static final Duration FAILURE_PAUSE = Duration.ofMinutes(1);
    private static final Duration ACCESS_DENIED_PAUSE = Duration.ofMinutes(30);
    // シーズン開始直後に前のシーズンの順位が取れず、少人数の今シーズンのランキングで集めた回は、24時間待たずに取り直す。
    private static final Duration SMALL_SAMPLE_REFRESH = Duration.ofHours(1);

    private final ClashRoyaleApiClient apiClient;
    private final CardUsageService usageService;
    private final TopBattleService battleService;
    private final CardUsageProperties properties;
    private final Clock clock;

    private Instant pausedUntil = Instant.MIN;
    private List<PlayerRankingResponse.RankedPlayer> players;
    private Set<String> playerTags;
    private YearMonth finishedSeason;
    private int next;
    private List<TopDecks.SampledDeck> decks;
    private Map<String, TopBattle> battles;

    TopDeckCollector(ClashRoyaleApiClient apiClient, CardUsageService usageService, TopBattleService battleService,
            CardUsageProperties properties, Clock clock) {
        this.apiClient = apiClient;
        this.usageService = usageService;
        this.battleService = battleService;
        this.properties = properties;
        this.clock = clock;
    }

    /** APIを1回呼ぶ分だけ進める。一定間隔で呼ばれる前提。 */
    void collectNext() {
        Instant now = clock.instant();
        if (now.isBefore(pausedUntil)) {
            return;
        }
        try {
            if (players == null) {
                if (upToDate(now)) {
                    return;
                }
                start(now);
                return;
            }
            collect(players.get(next));
            next++;
            if (next >= players.size()) {
                finish(now);
            }
        } catch (ApiAccessDeniedException e) {
            pause(ACCESS_DENIED_PAUSE, "access denied (check the allowed IP addresses of the API key)");
        } catch (ClashRoyaleApiException e) {
            // 429もここで一律に待つ。1人分ずつしか呼ばないので、巡回のような段階的な延長までは要らない。
            pause(FAILURE_PAUSE, e.toString());
        }
    }

    /**
     * 誰のデッキかを持たない古い形式の集計(2026-09-27より前)は、待たずに集め直す(トッププレイヤーのデッキ画面に要るため)。
     * 対戦を集め始める前の集計しか無いとき({@link TopBattleService#awaitingFirstCollection()})も待たない。
     */
    private boolean upToDate(Instant now) {
        if (battleService.awaitingFirstCollection()) {
            return false;
        }
        return usageService.topDecks()
                .filter(topDecks -> topDecks.decks().stream().allMatch(deck -> deck.player() != null))
                .filter(topDecks -> !outlivedNewSeasonPeriod(topDecks, now))
                .map(topDecks -> now.isBefore(topDecks.collectedAt().plus(refreshAfter(topDecks, now))))
                .orElse(false);
    }

    // 前のシーズンの順位で集めた集計は、シーズン開始直後の期間が終わったら、24時間を待たずに今シーズンで集め直す。
    private static boolean outlivedNewSeasonPeriod(TopDecks topDecks, Instant now) {
        Instant periodEnd = SeasonCalendar.newSeasonPeriodEnd(now);
        return topDecks.finishedSeason() != null && topDecks.collectedAt().isBefore(periodEnd)
                && !now.isBefore(periodEnd);
    }

    private Duration refreshAfter(TopDecks topDecks, Instant now) {
        boolean smallCurrentSeason = topDecks.finishedSeason() == null
                && topDecks.decks().size() < properties.players() / 2
                && SeasonCalendar.inNewSeasonPeriod(now);
        return smallCurrentSeason ? SMALL_SAMPLE_REFRESH : properties.refreshEvery();
    }

    private void start(Instant now) {
        List<PlayerRankingResponse.RankedPlayer> ranked =
                apiClient.getPathOfLegendRankings("global", properties.players());
        YearMonth season = null;
        // 今シーズンに1人もいなければ、期間を過ぎていても前のシーズンで集める(集めるものが無いため)。
        if (ranked.isEmpty()
                || (ranked.size() < properties.players() && SeasonCalendar.inNewSeasonPeriod(now))) {
            Optional<FinishedSeasonRanking> finished = latestFinishedSeason();
            if (finished.isPresent()) {
                ranked = finished.get().players();
                season = finished.get().season();
            }
        }
        if (ranked.isEmpty()) {
            pause(FAILURE_PAUSE, "the global player ranking is empty");
            return;
        }
        players = ranked;
        playerTags = ranked.stream().map(player -> Tags.normalize(player.tag())).collect(Collectors.toSet());
        finishedSeason = season;
        next = 0;
        decks = new ArrayList<>();
        battles = new LinkedHashMap<>();
        log.info("top deck collector: started collecting the decks of {} players{}", players.size(),
                season == null ? "" : " from the final ranking of " + season);
    }

    private record FinishedSeasonRanking(YearMonth season, List<PlayerRankingResponse.RankedPlayer> players) {
    }

    // 前のシーズンが無ければ今シーズンのランキング(少人数)で集める。一時的な失敗は呼び出し元で待って取り直す。
    private Optional<FinishedSeasonRanking> latestFinishedSeason() {
        try {
            String seasonId = apiClient.getLatestFinishedSeasonId();
            if (seasonId == null) {
                return Optional.empty();
            }
            List<PlayerRankingResponse.RankedPlayer> ranked =
                    apiClient.getFinishedSeasonPathOfLegendRankings(seasonId, properties.players());
            return ranked.isEmpty() ? Optional.empty()
                    : Optional.of(new FinishedSeasonRanking(YearMonth.parse(seasonId), ranked));
        } catch (ResourceNotFoundException | DateTimeParseException e) {
            log.warn("finished season player ranking unavailable for top decks: {}", e.toString());
            return Optional.empty();
        }
    }

    private void collect(PlayerRankingResponse.RankedPlayer ranked) {
        List<BattleLogEntry> battleLog;
        try {
            battleLog = apiClient.getBattleLogUncached(ranked.tag());
        } catch (ResourceNotFoundException e) {
            // ランキング取得後にアカウントが消えた場合など。数に入れずに次へ進む。
            return;
        }
        // 対戦履歴は新しい順(公式APIが返すのは通常30戦)。その中にランク戦が無い人は数に入れない(上位勢ではまず起きない)。
        battleLog.stream()
                .filter(TopDeckCollector::isRankedDeck)
                .findFirst()
                .map(battle -> battle.team().get(0))
                .ifPresent(me -> decks.add(sample(ranked, me)));
        // 同じ対戦は相手の対戦履歴にも出るので、まとめて1つにする。
        battleLog.stream()
                .filter(this::againstCollectedPlayer)
                .flatMap(battle -> TopBattle.of(battle).stream())
                .forEach(battle -> battles.put(battle.key(), battle));
    }

    private boolean againstCollectedPlayer(BattleLogEntry battle) {
        return battle.opponent() != null && battle.opponent().size() == 1 && battle.opponent().get(0).tag() != null
                && playerTags.contains(Tags.normalize(battle.opponent().get(0).tag()));
    }

    private static TopDecks.SampledDeck sample(PlayerRankingResponse.RankedPlayer ranked,
            BattleLogEntry.Participant me) {
        BattleLogEntry.Card tower = me.supportCards() == null || me.supportCards().isEmpty()
                ? null : me.supportCards().get(0);
        return new TopDecks.SampledDeck(
                me.cards().stream().map(BattleLogEntry.Card::id).toList(),
                tower == null ? null : tower.id(),
                new TopDecks.Player(ranked.tag(), GameText.stripFormatting(ranked.name()), ranked.rank(),
                        ranked.eloRating(),
                        me.cards().stream().map(card -> CardLevel.inGame(card.level(), card.maxLevel())).toList(),
                        tower == null ? null : CardLevel.inGame(tower.level(), tower.maxLevel()),
                        me.cards().stream().map(card -> CardForm.ofBattle(card.evolutionLevel())).toList()));
    }

    private static boolean isRankedDeck(BattleLogEntry battle) {
        return RANKED_BATTLE_TYPE.equals(battle.type())
                && battle.team() != null && battle.team().size() == 1
                && battle.team().get(0).cards() != null && battle.team().get(0).cards().size() == DECK_SIZE;
    }

    private void finish(Instant now) {
        usageService.publish(new TopDecks(now, List.copyOf(decks), finishedSeason));
        battleService.publish(List.copyOf(battles.values()));
        log.info("top deck collector: collected {} decks and {} battles between them from {} players", decks.size(),
                battles.size(), players.size());
        players = null;
        playerTags = null;
        finishedSeason = null;
        decks = null;
        battles = null;
    }

    private void pause(Duration duration, String reason) {
        pausedUntil = clock.instant().plus(duration);
        log.warn("top deck collector paused for {}: {}", duration, reason);
    }
}
