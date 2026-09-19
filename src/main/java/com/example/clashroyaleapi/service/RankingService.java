package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.ClanRankingResponse;
import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;
import com.example.clashroyaleapi.client.exception.ClashRoyaleApiException;
import com.example.clashroyaleapi.domain.PlayerSighting;
import com.example.clashroyaleapi.store.PlayerSightingLog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class RankingService {

    /** 全世界のランキングを指すlocationId。公式APIでは数値IDではなくこの文字列を使う。 */
    public static final String GLOBAL_LOCATION_ID = "global";

    private static final Logger log = LoggerFactory.getLogger(RankingService.class);

    // 個人ランキングと同じく、公式APIが返せる上限(実測で1000件確認)まで取得する。
    // 画面の注記にも件数を出すため公開している(文言と実際の件数がずれないようにするため)。
    public static final int CLAN_RANKING_SIZE = 1000;

    // クラン対戦トロフィーは、上位が軒並みクランスコアの上限(140000)で並ぶ状況(同点)を見分けるための補助指標。
    // クランごとに専用APIを叩く必要があるため(一覧APIには含まれない)、対象を上位だけに絞っている。
    public static final int WAR_TROPHIES_RANK_LIMIT = 20;

    // 個人ランキングを出す画面のうち、最も多く表示する件数。公式APIが返せる上限と同じ。
    public static final int MAX_PLAYER_RANKING_SIZE = 1000;

    private final ClashRoyaleApiClient apiClient;
    private final PlayerSightingLog sightingLog;

    public RankingService(ClashRoyaleApiClient apiClient, PlayerSightingLog sightingLog) {
        this.apiClient = apiClient;
        this.sightingLog = sightingLog;
    }

    /**
     * ランキングは付加的な情報で、取得に失敗しても画面の他の部分(国の選択など)は使えるべきなので、
     * ここでは例外をエラー画面に伝播させず空リストにする(画面側は「取得できません」と表示する)。
     */
    public List<ClanRankingResponse.RankedClan> topClans(String locationId) {
        try {
            return apiClient.getClanRankings(locationId, CLAN_RANKING_SIZE);
        } catch (ClashRoyaleApiException e) {
            log.warn("clan ranking unavailable for location {}: {}", locationId, e.toString());
            return List.of();
        }
    }

    /**
     * 個人ランキング(パス・オブ・レジェンドの現在シーズン)。失敗時の扱いはクランランキングと同じ。
     * 画面ごとに件数が違っても、公式APIからは常に最大件数で取得して先頭を切り出す。
     * 件数ごとに取得するとキャッシュのキーが分かれ、同じデータを画面の数だけ取り直すことになるため。
     */
    public List<PlayerRankingResponse.RankedPlayer> topPlayers(String locationId, int limit) {
        if (limit > MAX_PLAYER_RANKING_SIZE) {
            throw new IllegalArgumentException("limit must be <= " + MAX_PLAYER_RANKING_SIZE + ": " + limit);
        }
        try {
            List<PlayerRankingResponse.RankedPlayer> players =
                    apiClient.getPathOfLegendRankings(locationId, MAX_PLAYER_RANKING_SIZE);
            sightingLog.record(players.stream()
                    .map(player -> new PlayerSighting(player.tag(), player.name()))
                    .toList());
            return players.stream()
                    .limit(limit)
                    .toList();
        } catch (ClashRoyaleApiException e) {
            log.warn("player ranking unavailable for location {}: {}", locationId, e.toString());
            return List.of();
        }
    }

    /**
     * 上位{@value WAR_TROPHIES_RANK_LIMIT}クランのクラン対戦トロフィーを、クランタグをキーに返す。
     * 1クランずつ専用APIを叩く必要があるため、仮想スレッドで並行に取得して待ち時間を抑える
     * (getClanは2分キャッシュ済みなので、同じクランへの2回目以降の呼び出しは実質API通信が発生しない)。
     * 個別のクランで取得に失敗しても他のクランの表示に影響させないよう、そのクランだけ結果から除く。
     *
     * 集計結果自体もlocationIdをキーにキャッシュする。getClanは個々のクランについては2分キャッシュ済みだが、
     * それでも「20並行で取得する」処理自体を毎リクエスト実行すると、低スペックVM(1/8 OCPU)では
     * 仮想スレッドの起動コストだけで数秒かかることを本番で確認した(2026-09-19)。
     */
    @Cacheable(value = "clanWarTrophies", key = "#locationId")
    public Map<String, Integer> warTrophiesOfTopClans(String locationId, List<ClanRankingResponse.RankedClan> clans) {
        List<ClanRankingResponse.RankedClan> targets = clans.stream().limit(WAR_TROPHIES_RANK_LIMIT).toList();
        Map<String, Future<Integer>> futures = new LinkedHashMap<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (ClanRankingResponse.RankedClan clan : targets) {
                futures.put(clan.tag(), executor.submit(() -> apiClient.getClan(clan.tag()).clanWarTrophies()));
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        futures.forEach((tag, future) -> {
            try {
                result.put(tag, future.get());
            } catch (ExecutionException e) {
                log.warn("clan war trophies unavailable for {}: {}", tag, String.valueOf(e.getCause()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        return result;
    }
}
