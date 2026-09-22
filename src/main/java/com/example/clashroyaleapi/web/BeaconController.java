package com.example.clashroyaleapi.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * 実ブラウザで表示された回数を数えるためのビーコン。ページのJSから1回だけ叩かれる。
 *
 * アクセスログのUser-AgentやIPからの推定では、Chromeを偽装したスキャナーと本物の閲覧者を
 * 見分けきれない(2026-09-22に、Xからの流入に見えた26件がほぼ全て自動巡回だったことが判明)。
 * HTMLを取得するだけのクローラーはJSを実行しないため、ここに到達した数は
 * 「本当にブラウザで表示された回数」に近い。
 *
 * 記録先は専用のストレージを持たずApacheのアクセスログに任せる。集計は`access-check`スキルが
 * 既存のログから行うため、アプリ側は状態を持たない(DBを使わない方針とも揃う)。
 * 閲覧者を識別する値は一切受け取らない(Cookieも発行しない)ので、Cookie同意の対象にならない。
 */
@RestController
public class BeaconController {

    // navigator.sendBeaconはPOSTで送り、使えない環境向けのfetchはGETで送るため両方受ける。
    @RequestMapping(value = "/beacon", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Void> beacon() {
        // 中継やブラウザにキャッシュされると2回目以降が記録されないため、明示的に止める。
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}
