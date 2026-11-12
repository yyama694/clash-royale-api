package com.example.clashroyaleapi.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * プレイヤー・クランの画面の前に置く、通行証の確かめ(HumanCheckInterceptor)の設定。
 *
 * @param enabled      確かめるか。止めたいときにビルドし直さずに切れるよう、本番は環境変数で変えられる
 * @param exemptMobile iPhone・iPad・Androidを名乗るものを確かめないか。2026-11-12時点でボットはスマホをほぼ名乗らず、
 *                     人の大半はXアプリのiPhoneなので、人の手間を減らすため既定は対象外。ボットがスマホを名乗りはじめたら false にする
 * @param difficulty   確かめ用のページのJSに解かせる計算の重さ(SHA-256の先頭が0になるビット数)。10で平均1,024回、スマホで0.1〜0.3秒
 */
@Validated
@ConfigurationProperties(prefix = "human-check")
public record HumanCheckProperties(boolean enabled, boolean exemptMobile, @Min(0) @Max(20) int difficulty) {
}
