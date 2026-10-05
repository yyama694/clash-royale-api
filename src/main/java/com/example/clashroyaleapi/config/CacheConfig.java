package com.example.clashroyaleapi.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.CaffeineSpec;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 公式APIには呼び出し回数制限があり、VMもAlways Free枠の低スペックのため、
 * 同じタグへの短時間の連続アクセスはキャッシュで吸収する。
 *
 * 上限件数はキャッシュごとに分ける。1件あたりの重さが桁違いで、一律500件だと
 * 対戦履歴(1件で数百枚のカード)とランキング(1件で1000人)だけでヒープを数百MB占める。
 * 本番VMはヒープ1.4GB・1 OCPUのため、古い世代が埋まるとFull GCが止まらなくなり、
 * 画面表示が数秒止まる(2026-09-21に実測。進捗ログ参照)。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final String TTL = "expireAfterWrite=2m";

    // ランキングは順位が刻々と動き、古さが目に付きやすいので短くする(2026-10-05にユーザーが決定)。
    private static final String RANKING_TTL = "expireAfterWrite=1m";

    private static final String FINISHED_SEASON_TTL = "expireAfterWrite=1h";

    // 一覧に無い名前で @Cacheable を足しても動くよう、既定も持っておく。
    private static final String DEFAULT_SPEC = "maximumSize=200," + TTL;

    private static final Map<String, String> SPECS = Map.ofEntries(
            // 1件=プレイヤー1人(所持カード約120枚)。
            Map.entry("players", "maximumSize=300," + TTL),
            // 1件=クラン1つ(メンバー50人)。
            Map.entry("clans", "maximumSize=200," + TTL),
            // 1件=クラン1つの今週のクラン対戦(参加者は元メンバーを含め最大で数十人)。
            Map.entry("riverRaces", "maximumSize=200," + TTL),
            // 1件=直近25試合分の参加者とデッキ。1件で数百枚のカードになる、最も重いキャッシュ。
            Map.entry("battleLogs", "maximumSize=80," + TTL),
            Map.entry("clanSearches", "maximumSize=200," + TTL),
            // 1件=1000クラン / 1000人。国・地域の数だけ増え得るが、同時に見られるのは一部だけ。
            Map.entry("clanRankings", "maximumSize=40," + RANKING_TTL),
            Map.entry("playerRankings", "maximumSize=40," + RANKING_TTL),
            // 終わったシーズンの順位は変わらない。使うのは新シーズンの開始直後(今シーズンのランキングが空の間)だけ。
            Map.entry("finishedSeasons", "maximumSize=2," + FINISHED_SEASON_TTL),
            Map.entry("finishedSeasonPlayerRankings", "maximumSize=4," + FINISHED_SEASON_TTL),
            // 1件=タグ20件の小さなMap。クランランキングの列なので、ランキングと同じ時間で切る。
            Map.entry("clanWarTrophies", "maximumSize=200," + RANKING_TTL),
            // どちらも実質1キーしか無い。
            Map.entry("locations", "maximumSize=4," + TTL),
            Map.entry("cards", "maximumSize=4," + TTL));

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.from(CaffeineSpec.parse(DEFAULT_SPEC)));
        SPECS.forEach((name, spec) ->
                manager.registerCustomCache(name, Caffeine.from(CaffeineSpec.parse(spec)).build()));
        return manager;
    }
}
