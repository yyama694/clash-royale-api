package com.example.clashroyaleapi.domain;

import java.time.Instant;

/** 名前検索で見つかったプレイヤー。name は当サイトが最後に確認した時点の名前(装飾タグ込みの元の値)。 */
public record PlayerNameMatch(String tag, String name, Instant lastSeen) {
}
