package com.example.clashroyaleapi.crawler;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.config.RankingHistoryProperties;
import com.example.clashroyaleapi.service.RankingHistoryService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

import java.time.Clock;
import java.time.ZoneOffset;

/** ranking-history.enabled=true のときだけ、ランク戦の世界ランキングを記録する。 */
@Configuration
@ConditionalOnProperty(name = "ranking-history.enabled", havingValue = "true")
class RankingRecorderConfig implements SchedulingConfigurer {

    private final RankingRecorder recorder;
    private final RankingHistoryProperties properties;

    RankingRecorderConfig(ClashRoyaleApiClient apiClient, RankingHistoryService historyService,
            RankingHistoryProperties properties) {
        this.properties = properties;
        this.recorder = new RankingRecorder(apiClient, historyService, Clock.systemUTC());
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addCronTask(new CronTask(recorder::record, new CronTrigger(properties.cron(), ZoneOffset.UTC)));
    }
}
