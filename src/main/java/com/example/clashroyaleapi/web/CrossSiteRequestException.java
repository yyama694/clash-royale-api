package com.example.clashroyaleapi.web;

/** 他サイトから送られてきた状態変更のリクエストを拒否したことを表す。詳細は {@link CrossSiteRequestGuard}。 */
public class CrossSiteRequestException extends RuntimeException {

    public CrossSiteRequestException(String message) {
        super(message);
    }
}
