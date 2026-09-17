package com.example.clashroyaleapi.client;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiTimestampTest {

    @Test
    void 公式APIの形式をUTCとして解釈する() {
        assertEquals(Instant.parse("2026-09-15T09:21:24Z"), ApiTimestamp.parse("20260915T092124.000Z").orElseThrow());
    }

    @Test
    void 前後の空白は無視する() {
        assertEquals(Instant.parse("2026-09-15T09:21:24Z"), ApiTimestamp.parse(" 20260915T092124.000Z ").orElseThrow());
    }

    @Test
    void 解釈できない値は空を返す() {
        assertTrue(ApiTimestamp.parse(null).isEmpty());
        assertTrue(ApiTimestamp.parse("").isEmpty());
        assertTrue(ApiTimestamp.parse("2026-09-15T09:21:24Z").isEmpty());
        assertTrue(ApiTimestamp.parse("20261315T092124.000Z").isEmpty());
    }
}
