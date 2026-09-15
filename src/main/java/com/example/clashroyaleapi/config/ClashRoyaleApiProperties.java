package com.example.clashroyaleapi.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * tokenが未設定のまま起動すると全リクエストが403になり原因が分かりにくいため、
 * @NotBlank を付けて起動時点で失敗させる。
 */
@Validated
@ConfigurationProperties(prefix = "clashroyale.api")
public record ClashRoyaleApiProperties(@NotBlank String baseUrl, @NotBlank String token) {
}
