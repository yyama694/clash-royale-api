package com.example.clashroyaleapi.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * 公式APIには呼び出し回数制限があり、VMもAlways Free枠の低スペックのため、
 * 同じタグへの短時間の連続アクセスはキャッシュで吸収する(TTLは application.yml 側で指定)。
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
