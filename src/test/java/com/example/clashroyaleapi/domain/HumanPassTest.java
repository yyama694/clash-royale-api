package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumanPassTest {

    private static final byte[] KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");
    private static final int DIFFICULTY = 6;

    private static HumanPass at(Instant instant) {
        return new HumanPass(KEY, DIFFICULTY, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static String answer(String challenge, int bits) {
        for (int a = 0; ; a++) {
            if (HumanPass.leadingZeroBits(HumanPass.sha256(challenge + ":" + a)) >= bits) {
                return Integer.toString(a);
            }
        }
    }

    private static String wrongAnswer(String challenge) {
        for (int a = 0; ; a++) {
            if (HumanPass.leadingZeroBits(HumanPass.sha256(challenge + ":" + a)) < DIFFICULTY) {
                return Integer.toString(a);
            }
        }
    }

    private static String passFor(String ip) {
        HumanPass humanPass = at(NOW);
        String challenge = humanPass.challenge(ip).orElseThrow();
        return humanPass.solve(ip, challenge, answer(challenge, DIFFICULTY)).orElseThrow();
    }

    @Test
    void 問題を解けば通行証が出て_同じIPの範囲では有効() {
        String pass = passFor("203.0.113.10");
        assertTrue(at(NOW).isValidPass("203.0.113.10", pass));
        // 携帯回線などで末尾が変わっても、同じ/24なら確かめ直しにしない。
        assertTrue(at(NOW).isValidPass("203.0.113.250", pass));
    }

    @Test
    void 別のIPの範囲では通行証を使い回せない() {
        String pass = passFor("203.0.113.10");
        assertFalse(at(NOW).isValidPass("203.0.114.10", pass));
        assertFalse(at(NOW).isValidPass("2001:db8::1", pass));
    }

    @Test
    void 通行証は30日で切れる() {
        String pass = passFor("203.0.113.10");
        assertTrue(at(NOW.plus(HumanPass.PASS_TTL).minusSeconds(1)).isValidPass("203.0.113.10", pass));
        assertFalse(at(NOW.plus(HumanPass.PASS_TTL)).isValidPass("203.0.113.10", pass));
    }

    @Test
    void 書き換えた通行証や別の鍵で作った通行証は無効() {
        String pass = passFor("203.0.113.10");
        int dot = pass.indexOf('.');
        // 期限だけを延ばしても、署名が合わない。
        String extended = (Long.parseLong(pass.substring(0, dot)) + 86400) + pass.substring(dot);
        assertFalse(at(NOW).isValidPass("203.0.113.10", extended));
        String flipped = pass.substring(0, pass.length() - 1) + (pass.endsWith("A") ? "B" : "A");
        assertFalse(at(NOW).isValidPass("203.0.113.10", flipped));
        HumanPass otherKey = new HumanPass("another-key-another-key-another!".getBytes(StandardCharsets.US_ASCII),
                DIFFICULTY, Clock.fixed(NOW, ZoneOffset.UTC));
        assertFalse(otherKey.isValidPass("203.0.113.10", pass));
    }

    @Test
    void 形の崩れた通行証は無効() {
        HumanPass humanPass = at(NOW);
        assertFalse(humanPass.isValidPass("203.0.113.10", null));
        assertFalse(humanPass.isValidPass("203.0.113.10", ""));
        assertFalse(humanPass.isValidPass("203.0.113.10", "1"));
        assertFalse(humanPass.isValidPass("203.0.113.10", "abc.def"));
        assertFalse(humanPass.isValidPass("203.0.113.10", ".sig"));
        assertFalse(humanPass.isValidPass("203.0.113.10", "9999999999."));
    }

    @Test
    void 問題そのものは通行証として使えない() {
        String challenge = at(NOW).challenge("203.0.113.10").orElseThrow();
        // 問題の時刻(NOW)がまだ来ていない時点なら期限の条件は満たすが、署名の種類が違うので通らない。
        assertFalse(at(NOW.minusSeconds(60)).isValidPass("203.0.113.10", challenge));
    }

    @Test
    void 答えが条件を満たさなければ通行証を出さない() {
        HumanPass humanPass = at(NOW);
        String challenge = humanPass.challenge("203.0.113.10").orElseThrow();
        assertEquals(Optional.empty(), humanPass.solve("203.0.113.10", challenge, wrongAnswer(challenge)));
        assertEquals(Optional.empty(), humanPass.solve("203.0.113.10", challenge, ""));
        assertEquals(Optional.empty(), humanPass.solve("203.0.113.10", challenge, null));
        assertEquals(Optional.empty(), humanPass.solve("203.0.113.10", null, "1"));
    }

    @Test
    void 問題は10分で切れ_発行した範囲の外では解けない() {
        String challenge = at(NOW).challenge("203.0.113.10").orElseThrow();
        String answer = answer(challenge, DIFFICULTY);
        assertTrue(at(NOW.plus(Duration.ofMinutes(10))).solve("203.0.113.10", challenge, answer).isPresent());
        assertFalse(at(NOW.plus(Duration.ofMinutes(10)).plusSeconds(1)).solve("203.0.113.10", challenge, answer).isPresent());
        assertFalse(at(NOW).solve("198.51.100.10", challenge, answer).isPresent());
        // 発行時刻を未来に書き換えた問題も受け付けない。
        assertFalse(at(NOW.minusSeconds(1)).solve("203.0.113.10", challenge, answer).isPresent());
    }

    @Test
    void IPの範囲はIPv4が24ビット_IPv6が64ビット() {
        assertEquals(HumanPass.ipRange("203.0.113.10"), HumanPass.ipRange("203.0.113.99"));
        assertEquals(HumanPass.ipRange("2001:db8:1:2:aaaa::1"), HumanPass.ipRange("2001:db8:1:2:bbbb::2"));
        assertFalse(HumanPass.ipRange("2001:db8:1:2::1").equals(HumanPass.ipRange("2001:db8:1:3::1")));
        // IPv4をIPv6の形で書いたものは、IPv4として扱う。
        assertEquals(HumanPass.ipRange("203.0.113.10"), HumanPass.ipRange("::ffff:203.0.113.10"));
    }

    @Test
    void IPの形でない値には範囲を作らない() {
        // DNSに問い合わせる形(ホスト名など)も含めて、範囲を作らない。
        for (String value : new String[] {null, "", "abc", "localhost", "999.1.1.1", "1.2.3", "1.2.3.4.5", "::zz", "1.2.3.4 "}) {
            assertEquals(Optional.empty(), HumanPass.ipRange(value), String.valueOf(value));
        }
        assertEquals(Optional.empty(), at(NOW).challenge("abc"));
    }

    @Test
    void 先頭の0のビット数を数える() {
        assertEquals(0, HumanPass.leadingZeroBits(new byte[] {(byte) 0x80, 0}));
        assertEquals(7, HumanPass.leadingZeroBits(new byte[] {1, 0}));
        assertEquals(12, HumanPass.leadingZeroBits(new byte[] {0, 0x0f}));
        assertEquals(16, HumanPass.leadingZeroBits(new byte[] {0, 0}));
    }
}
