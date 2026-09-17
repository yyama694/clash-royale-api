package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.ClanRankingResponse;
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

    // トップページの表示量と、低スペックVMでの描画コストのバランスで上位10件にしている。
    private static final int RANKING_LIMIT = 10;

    private final ClashRoyaleApiClient apiClient;

    public RankingService(ClashRoyaleApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * ランキング取得に失敗してもトップページの検索機能は使えるべきなので、
     * ここでは例外をエラー画面に伝播させず空リストにする(画面側は「取得できません」と表示する)。
     */
    public List<ClanRankingResponse.RankedClan> topClans(String locationId) {
        try {
            return apiClient.getClanRankings(locationId, RANKING_LIMIT);
        } catch (ClashRoyaleApiException e) {
            log.warn("clan ranking unavailable for location {}: {}", locationId, e.toString());
            return List.of();
        }
    }
}
