package com.example.clashroyaleapi.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * ランキングの動き画面のために、ランク戦の世界ランキングを記録する処理(RankingRecorder)の設定。
 *
 * @param cron 記録する時刻(UTC)。間隔を変えたら、画面の注記(movements.notice)の「15分ごと」も直す
 */
@Validated
@ConfigurationProperties(prefix = "ranking-history")
public record RankingHistoryProperties(boolean enabled, @NotBlank String cron) {
}
