package com.example.clashroyaleapi.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 名前検索用にプレイヤーを集める巡回の設定。本番では /etc/clash-royale-api/env で変えられるよう環境変数から読む。
 *
 * @param racesPerClan   1クランあたりに取得するクラン対戦の件数。多いほど1回で集まる人数は増えるが応答も大きくなる
 * @param battleLogEvery 何回に1回をプレイヤーの対戦履歴の巡回にするか(残りはクラン対戦の履歴)
 */
@Validated
@ConfigurationProperties(prefix = "crawler")
public record CrawlerProperties(boolean enabled, @NotNull Duration interval,
                                @Min(1) @Max(10) int racesPerClan,
                                @Min(1) int battleLogEvery) {
}
