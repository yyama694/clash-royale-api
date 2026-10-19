package com.example.clashroyaleapi.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.LocalTime;

/**
 * カード詳細画面の「世界トップのデッキでの使用率」を集める処理(TopDeckCollector)の設定。
 *
 * @param interval 1人分のデッキを取得する間隔。巡回(Crawler)とは別に公式APIを呼ぶので、詰めすぎない
 * @param startAt  毎日この時刻(UTC)に集め始める
 * @param players  ランク戦の世界ランキングの上位何人を集計するか(公式APIが返せるのは1000人まで)
 */
@Validated
@ConfigurationProperties(prefix = "card-usage")
public record CardUsageProperties(boolean enabled, @NotNull Duration interval, @NotNull LocalTime startAt,
                                  @Min(1) @Max(1000) int players) {
}
