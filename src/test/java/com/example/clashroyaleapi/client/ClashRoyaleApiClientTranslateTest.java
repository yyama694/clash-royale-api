package com.example.clashroyaleapi.client;

import com.example.clashroyaleapi.client.exception.ApiMaintenanceException;
import com.example.clashroyaleapi.client.exception.ApiUnavailableException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ClashRoyaleApiClientTranslateTest {

    private static HttpServerErrorException serviceUnavailable(String body) {
        return HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", null,
                body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    @Test
    void メンテナンス中の503はメンテナンスとして扱う() {
        var e = ClashRoyaleApiClient.translate(serviceUnavailable(
                "{\"reason\":\"inMaintenance\",\"message\":\"API is currently in maintenance, please come back later\"}"));

        assertInstanceOf(ApiMaintenanceException.class, e);
        assertEquals("error.maintenance", e.messageKey());
    }

    @Test
    void メンテナンス以外の503は通常の接続不可として扱う() {
        var e = ClashRoyaleApiClient.translate(serviceUnavailable("{\"reason\":\"serviceUnavailable\"}"));

        assertEquals(ApiUnavailableException.class, e.getClass());
        assertEquals("error.unavailable", e.messageKey());
    }
}
