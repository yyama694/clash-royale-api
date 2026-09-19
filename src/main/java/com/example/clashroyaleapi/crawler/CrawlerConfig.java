package com.example.clashroyaleapi.crawler;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.config.CrawlerProperties;
import com.example.clashroyaleapi.config.PlayerIndexProperties;
import com.example.clashroyaleapi.store.PlayerSightingLog;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.nio.file.Path;
import java.time.Clock;

/**
 * crawler.enabled=true のときだけ巡回を動かす。
 * 別のJVMにしないのは、今のVM(使えるメモリ約340MB)では2つ目のJVMを載せる余裕が無いため。
 */
@Configuration
@ConditionalOnProperty(name = "crawler.enabled", havingValue = "true")
class CrawlerConfig implements SchedulingConfigurer {

    private final Crawler crawler;
    private final CrawlerProperties properties;

    CrawlerConfig(ClashRoyaleApiClient apiClient, PlayerSightingLog sightingLog,
                  CrawlerProperties properties, PlayerIndexProperties indexProperties) {
        this.properties = properties;
        Path dir = Path.of(indexProperties.dir()).resolve("crawler");
        // 周回の切り替えで重複を除くとき、1ファイル分(全体の1/shards)をメモリに載せる。
        // プレイヤーは数千万件になり得るので、1ファイルが数十万件に収まるよう細かく分ける。
        this.crawler = new Crawler(apiClient, sightingLog,
                new TagQueue(dir.resolve("clans"), 16), new TagQueue(dir.resolve("players"), 256),
                properties, Clock.systemUTC());
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(crawler::crawlNext, properties.interval());
    }
}
