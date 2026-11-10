package com.example.clashroyaleapi.crawler;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.config.RankingHistoryProperties;
import com.example.clashroyaleapi.service.RankingHistoryService;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.time.Clock;
import java.time.ZoneOffset;

/**
 * ranking-history.enabled=true のときだけ、ランク戦の世界ランキングを記録する。
 * 記録は取り逃すと後から埋められないので、ほかの定期処理が使う共通のスレッド(1本)には載せず、専用のスレッドで動かす。
 * 共通のスレッドだと、索引の整理(1回で1時間以上かかることがある)の間の記録が黙って飛んでいた(2026-11-04に46回)。
 * スケジューラーはBeanにしない。TaskSchedulerのBeanがあると、@Scheduledの既定のスケジューラーとして使われてしまう。
 */
@Configuration
@ConditionalOnProperty(name = "ranking-history.enabled", havingValue = "true")
class RankingRecorderConfig implements InitializingBean, DisposableBean {

    private final RankingRecorder recorder;
    private final RankingHistoryProperties properties;
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

    RankingRecorderConfig(ClashRoyaleApiClient apiClient, RankingHistoryService historyService,
            RankingHistoryProperties properties) {
        this.properties = properties;
        this.recorder = new RankingRecorder(apiClient, historyService, Clock.systemUTC());
    }

    @Override
    public void afterPropertiesSet() {
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ranking-recorder-");
        scheduler.initialize();
        scheduler.schedule(recorder::record, new CronTrigger(properties.cron(), ZoneOffset.UTC));
    }

    @Override
    public void destroy() {
        scheduler.shutdown();
    }
}
