package com.example.clashroyaleapi.client.exception;

/** 公式APIの呼び出し回数制限に達した(429)。 */
public class ApiRateLimitException extends ClashRoyaleApiException {

    public ApiRateLimitException(String detail, Throwable cause) {
        super("error.rateLimit", detail, cause);
    }
}
