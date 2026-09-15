package com.example.clashroyaleapi.client.exception;

/**
 * Clash Royale公式APIの呼び出し失敗を表す。
 * web層がSpringのHTTPクライアント例外(RestClientResponseException等)を直接知らなくて済むよう、
 * client層でこの型に変換してから投げる。messageKeyは画面表示用メッセージの解決に使う。
 */
public class ClashRoyaleApiException extends RuntimeException {

    private final String messageKey;

    protected ClashRoyaleApiException(String messageKey, String detail, Throwable cause) {
        super(detail, cause);
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }
}
