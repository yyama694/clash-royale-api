package com.example.clashroyaleapi.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param nameSearchEnabled プレイヤー検索で名前も受け付けるか。検索結果からの削除依頼の窓口を用意するまでは公開しないため、
 *                          既定は無効(無効の間はこれまでどおり入力をタグとして扱う)
 */
@Validated
@ConfigurationProperties(prefix = "player-index")
public record PlayerIndexProperties(@NotBlank String dir, boolean nameSearchEnabled) {
}
