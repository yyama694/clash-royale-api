package com.example.clashroyaleapi.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 新しくできたページをIndexNowで検索エンジンに知らせる処理(IndexNowSubmitter)の設定。
 *
 * @param key     検索エンジンに見せる目印。/{key}.txt で配信する。IndexNowの決まりで英数字とハイフン8〜128文字
 * @param siteUrl 送るURLの先頭(末尾の / なし)。定期処理にはリクエストが無く、アクセスされたホストから組み立てられないため
 * @param cron    送る時刻(UTC)
 */
@Validated
@ConfigurationProperties(prefix = "indexnow")
public record IndexNowProperties(boolean enabled, @Pattern(regexp = "[A-Za-z0-9-]{8,128}") String key,
        @NotBlank String siteUrl, @NotBlank String cron) {
}
