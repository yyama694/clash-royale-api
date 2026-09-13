package com.example.clashroyaleapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clashroyale.api")
public record ClashRoyaleApiProperties(String baseUrl, String token) {
}
