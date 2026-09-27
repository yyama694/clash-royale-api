package com.example.clashroyaleapi.crawler;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.config.CardUsageProperties;
import com.example.clashroyaleapi.service.CardUsageService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Clock;

/** card-usage.enabled=true のときだけ、世界トップのデッキ集計を動かす。 */
@Configuration
@ConditionalOnProperty(name = "card-usage.enabled", havingValue = "true")
class TopDeckCollectorConfig implements SchedulingConfigurer {

    private final TopDeckCollector collector;
    private final CardUsageProperties properties;

    TopDeckCollectorConfig(ClashRoyaleApiClient apiClient, CardUsageService usageService,
                           CardUsageProperties properties) {
        this.properties = properties;
        this.collector = new TopDeckCollector(apiClient, usageService, properties, Clock.systemUTC());
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(collector::collectNext, properties.interval());
    }
}
