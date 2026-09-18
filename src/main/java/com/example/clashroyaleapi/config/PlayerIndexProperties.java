package com.example.clashroyaleapi.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "player-index")
public record PlayerIndexProperties(@NotBlank String dir) {
}
