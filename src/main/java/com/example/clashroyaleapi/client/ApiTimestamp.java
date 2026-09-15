package com.example.clashroyaleapi.client;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * 公式APIの日時表記(例: 20260915T092124.000Z)をInstantに変換する。
 * lastSeen・battleTime で共通の形式で、タイムゾーンは常にUTC。
 */
public final class ApiTimestamp {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'");

    private ApiTimestamp() {
    }

    /** 解釈できない値(null・形式変更など)は空を返し、画面側でフォールバックさせる。 */
    public static Optional<Instant> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(raw.strip(), FORMAT).toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
