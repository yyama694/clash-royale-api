package com.example.clashroyaleapi.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param nameSearchEnabled プレイヤー検索で名前も受け付けるか。既定は無効(無効の間は入力をすべてタグとして扱う)。
 *                          本番は環境変数で有効にしている
 */
@Validated
@ConfigurationProperties(prefix = "player-index")
public record PlayerIndexProperties(@NotBlank String dir, boolean nameSearchEnabled) {
}
