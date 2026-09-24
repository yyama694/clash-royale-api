package com.example.clashroyaleapi.client.exception;

/**
 * 公式APIが計画メンテナンス中(503 + reason "inMaintenance")。
 * 障害と区別して「メンテナンス中」と言い切れるよう、ApiUnavailableExceptionのサブクラスとして分けている。
 * サブクラスにしているのは、「一時的に使えない」として扱う既存の箇所(巡回・部分表示など)をそのまま効かせるため。
 */
public class ApiMaintenanceException extends ApiUnavailableException {

    public ApiMaintenanceException(String detail, Throwable cause) {
        super("error.maintenance", detail, cause);
    }
}
