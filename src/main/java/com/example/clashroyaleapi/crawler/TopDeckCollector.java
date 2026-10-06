package com.example.clashroyaleapi.crawler;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;
import com.example.clashroyaleapi.client.exception.ApiAccessDeniedException;
import com.example.clashroyaleapi.client.exception.ClashRoyaleApiException;
import com.example.clashroyaleapi.client.exception.ResourceNotFoundException;
import com.example.clashroyaleapi.config.CardUsageProperties;
import com.example.clashroyaleapi.domain.CardForm;
import com.example.clashroyaleapi.domain.CardLevel;
import com.example.clashroyaleapi.domain.GameText;
import com.example.clashroyaleapi.domain.TopDecks;
import com.example.clashroyaleapi.service.CardUsageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
 * 今シーズンのランキングが目標の人数に満たない間は、前のシーズンの最終順位の上位で集める(2026-10-07にユーザーが決定)。
 */
class TopDeckCollector {

    private static final Logger log = LoggerFactory.getLogger(TopDeckCollector.class);

    private static final int DECK_SIZE = 8;
    private static final String RANKED_BATTLE_TYPE = "pathOfLegend";
    private static final Duration FAILURE_PAUSE = Duration.ofMinutes(1);
    private static final Duration ACCESS_DENIED_PAUSE = Duration.ofMinutes(30);
    // 前のシーズンの順位が取れず、少人数の今シーズンのランキングで集めた回は、24時間待たずに取り直す。
    private static final Duration SMALL_SAMPLE_REFRESH = Duration.ofHours(1);

    private final ClashRoyaleApiClient apiClient;
    private final CardUsageService usageService;
    private final CardUsageProperties properties;
    private final Clock clock;

    private Instant pausedUntil = Instant.MIN;
    private List<PlayerRankingResponse.RankedPlayer> players;
    private YearMonth finishedSeason;
    private int next;
    private List<TopDecks.SampledDeck> decks;

    TopDeckCollector(ClashRoyaleApiClient apiClient, CardUsageService usageService, CardUsageProperties properties,
            Clock clock) {
        this.apiClient = apiClient;
        this.usageService = usageService;
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
                start();
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

    /** 誰のデッキかを持たない古い形式の集計(2026-09-27より前)は、待たずに集め直す(トッププレイヤーのデッキ画面に要るため)。 */
    private boolean upToDate(Instant now) {
        return usageService.topDecks()
                .filter(topDecks -> topDecks.decks().stream().allMatch(deck -> deck.player() != null))
                .map(topDecks -> now.isBefore(topDecks.collectedAt().plus(refreshAfter(topDecks))))
                .orElse(false);
    }

    private Duration refreshAfter(TopDecks topDecks) {
        boolean smallCurrentSeason = topDecks.finishedSeason() == null
                && topDecks.decks().size() < properties.players() / 2;
        return smallCurrentSeason ? SMALL_SAMPLE_REFRESH : properties.refreshEvery();
    }

    private void start() {
        List<PlayerRankingResponse.RankedPlayer> ranked =
                apiClient.getPathOfLegendRankings("global", properties.players());
        YearMonth season = null;
        if (ranked.size() < properties.players()) {
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
        finishedSeason = season;
        next = 0;
        decks = new ArrayList<>();
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
        List<BattleLogEntry> battles;
        try {
            battles = apiClient.getBattleLogUncached(ranked.tag());
        } catch (ResourceNotFoundException e) {
            // ランキング取得後にアカウントが消えた場合など。数に入れずに次へ進む。
            return;
        }
        // 対戦履歴は新しい順(公式APIが返すのは通常30戦)。その中にランク戦が無い人は数に入れない(上位勢ではまず起きない)。
        battles.stream()
                .filter(TopDeckCollector::isRankedDeck)
                .findFirst()
                .map(battle -> battle.team().get(0))
                .ifPresent(me -> decks.add(sample(ranked, me)));
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
        log.info("top deck collector: collected {} decks from {} players", decks.size(), players.size());
        players = null;
        finishedSeason = null;
        decks = null;
    }

    private void pause(Duration duration, String reason) {
        pausedUntil = clock.instant().plus(duration);
        log.warn("top deck collector paused for {}: {}", duration, reason);
    }
}
