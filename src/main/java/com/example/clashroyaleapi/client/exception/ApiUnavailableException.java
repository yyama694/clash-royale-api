package com.example.clashroyaleapi.client.exception;

/** 公式API側の障害・メンテナンス(5xx)、またはタイムアウトや名前解決失敗などの通信エラー。 */
public class ApiUnavailableException extends ClashRoyaleApiException {

    public ApiUnavailableException(String detail, Throwable cause) {
        super("error.unavailable", detail, cause);
    }
}
