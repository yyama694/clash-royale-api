package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.ClanRankingResponse;
import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;
import com.example.clashroyaleapi.client.exception.ClashRoyaleApiException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RankingService {

    /** 全世界のランキングを指すlocationId。公式APIでは数値IDではなくこの文字列を使う。 */
    public static final String GLOBAL_LOCATION_ID = "global";

    private static final Logger log = LoggerFactory.getLogger(RankingService.class);

    // クランランキング画面の表示量と、低スペックVMでの描画コストのバランスで上位10件にしている。
    // 画面の注記にも件数を出すため公開している(文言と実際の件数がずれないようにするため)。
    public static final int CLAN_RANKING_SIZE = 10;

    // 個人ランキングを出す画面のうち、最も多く表示する件数。公式APIは最大1000件まで返すが、
    // 低スペックVMでの描画コストを考えて200件にしている。
    public static final int MAX_PLAYER_RANKING_SIZE = 200;

    private final ClashRoyaleApiClient apiClient;

    public RankingService(ClashRoyaleApiClient apiClient) {
        this.apiClient = apiClient;
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
            return apiClient.getPathOfLegendRankings(locationId, MAX_PLAYER_RANKING_SIZE).stream()
                    .limit(limit)
                    .toList();
        } catch (ClashRoyaleApiException e) {
            log.warn("player ranking unavailable for location {}: {}", locationId, e.toString());
            return List.of();
        }
    }
}
