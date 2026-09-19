package com.example.clashroyaleapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定期実行(巡回と、inboxの整理)を有効にする。スレッドは既定の1本のままにし、
 * 巡回と整理が同時に動かないようにしている(低スペックのVMでディスクとCPUを取り合わないため)。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
