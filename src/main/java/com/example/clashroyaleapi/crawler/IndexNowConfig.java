package com.example.clashroyaleapi.crawler;

import com.example.clashroyaleapi.client.IndexNowClient;
import com.example.clashroyaleapi.config.IndexNowProperties;
import com.example.clashroyaleapi.config.PlayerIndexProperties;
import com.example.clashroyaleapi.service.RankingHistoryService;
import com.example.clashroyaleapi.service.RankingService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneOffset;

/** indexnow.enabled=true のときだけ、新しくできたページをIndexNowで知らせる。 */
@Configuration
@ConditionalOnProperty(name = "indexnow.enabled", havingValue = "true")
class IndexNowConfig implements SchedulingConfigurer {

    // 起動直後はランキングの記録の読み込みや他の定期処理と重なるので、少し待つ。
    private static final Duration FIRST_RUN_DELAY = Duration.ofMinutes(2);

    private final IndexNowSubmitter submitter;
    private final IndexNowProperties properties;

    IndexNowConfig(IndexNowClient client, RankingHistoryService historyService, RankingService rankingService,
            IndexNowProperties properties, PlayerIndexProperties playerIndexProperties) {
        this.properties = properties;
        this.submitter = new IndexNowSubmitter(client, historyService, rankingService, properties,
                Path.of(playerIndexProperties.dir()).resolve("indexnow").resolve("submitted.txt"));
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addCronTask(new CronTask(submitter::submit, new CronTrigger(properties.cron(), ZoneOffset.UTC)));
        registrar.addOneTimeTask(submitter::submit, FIRST_RUN_DELAY);
    }
}
