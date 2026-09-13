package com.example.clashroyaleapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clashroyale.name-index")
public record NameIndexProperties(String file) {
}
