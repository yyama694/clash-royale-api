package com.example.clashroyaleapi.crawler;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;
import com.example.clashroyaleapi.client.exception.ClashRoyaleApiException;
import com.example.clashroyaleapi.domain.GameText;
import com.example.clashroyaleapi.domain.RankingSnapshot;
import com.example.clashroyaleapi.service.RankingHistoryService;
import com.example.clashroyaleapi.service.RankingService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * ランキングの動き画面のために、ランク戦の世界ランキング上位を記録する。決まった時刻に呼ばれる前提。
 * 公式APIは今のランキングしか返さないので、取り逃した時刻の分は後から埋められない。
 */
class RankingRecorder {

    private static final Logger log = LoggerFactory.getLogger(RankingRecorder.class);

    private final ClashRoyaleApiClient apiClient;
    private final RankingHistoryService historyService;
    private final Clock clock;

    RankingRecorder(ClashRoyaleApiClient apiClient, RankingHistoryService historyService, Clock clock) {
        this.apiClient = apiClient;
        this.historyService = historyService;
        this.clock = clock;
    }

    void record() {
        List<PlayerRankingResponse.RankedPlayer> ranked;
        String finishedSeason;
        try {
            ranked = apiClient.getPathOfLegendRankings(RankingService.GLOBAL_LOCATION_ID,
                    RankingService.MAX_PLAYER_RANKING_SIZE);
            if (ranked.isEmpty()) {
                // シーズンの切り替え直後は今シーズンのランキングが空になる。空の記録は残さない。
                log.info("ranking recorder: skipped because the global player ranking is empty");
                return;
            }
            // シーズンの区別に使うので、取れなければ記録しない(どのシーズンか分からない記録は比べられない)。
            finishedSeason = apiClient.getLatestFinishedSeasonId();
        } catch (ClashRoyaleApiException e) {
            log.warn("ranking recorder: skipped: {}", e.toString());
            return;
        }
        // 保存先のファイル名が時分秒なので、秒に切りそろえる。
        RankingSnapshot snapshot = new RankingSnapshot(clock.instant().truncatedTo(ChronoUnit.SECONDS),
                finishedSeason, ranked.stream()
                        .map(player -> new RankingSnapshot.Entry(player.rank(), player.tag(),
                                GameText.stripFormatting(player.name()), player.eloRating()))
                        .toList());
        try {
            historyService.record(snapshot);
        } catch (IOException e) {
            log.warn("ranking recorder: could not save the record: {}", e.toString());
        }
    }
}
