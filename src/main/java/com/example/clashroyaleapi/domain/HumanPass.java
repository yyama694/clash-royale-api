package com.example.clashroyaleapi.domain;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * プレイヤー・クランの画面を見るための通行証(正体を隠したボット対策。2026-11-12)。
 *
 * ボットは毎回別のIPから来て、JSを動かさず、UA・Referer・固定のCookieの値は1日で真似してきた。
 * そこで、通行証を「IPの範囲(IPv4は/24、IPv6は/64)と期限」にサーバーの鍵で署名した値にし、
 * 真似もIPをまたいだ使い回しもできないようにする。通行証は、確かめ用のページのJSが小さな計算
 * (SHA-256の先頭が{@code difficulty}ビット0になる答えを探す)を解いたときだけ出す。
 *
 * 値の形はどちらも「発行時刻または期限(エポック秒).署名」。鍵は起動ごとに作り直すので、
 * 再起動すると全員が1回だけ確かめ直しになる(鍵を保存する場所を持たないため)。
 */
public final class HumanPass {

    static final Duration CHALLENGE_TTL = Duration.ofMinutes(10);
    public static final Duration PASS_TTL = Duration.ofDays(30);
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern IPV6_CHARS = Pattern.compile("[0-9A-Fa-f:.]+");

    private final byte[] key;
    private final int difficulty;
    private final Clock clock;

    public HumanPass(byte[] key, int difficulty, Clock clock) {
        this.key = key.clone();
        this.difficulty = difficulty;
        this.clock = clock;
    }

    public int difficulty() {
        return difficulty;
    }

    /** 確かめ用のページに埋め込む問題。発行したIPの範囲でだけ、{@link #CHALLENGE_TTL}の間有効。IPの形でなければ空。 */
    public Optional<String> challenge(String ip) {
        long issuedAt = clock.instant().getEpochSecond();
        return Optional.ofNullable(sign("c", ip, issuedAt)).map(signature -> issuedAt + "." + signature);
    }

    /** 問題が本物・期限内・同じIPの範囲で、答えが条件を満たしていれば、通行証を返す。 */
    public Optional<String> solve(String ip, String challenge, String answer) {
        if (answer == null || answer.isEmpty() || answer.length() > 20
                || !valid("c", ip, challenge, CHALLENGE_TTL.toSeconds(), true)
                || leadingZeroBits(sha256(challenge + ":" + answer)) < difficulty) {
            return Optional.empty();
        }
        long expiresAt = clock.instant().plus(PASS_TTL).getEpochSecond();
        return Optional.of(expiresAt + "." + sign("p", ip, expiresAt));
    }

    public boolean isValidPass(String ip, String pass) {
        return valid("p", ip, pass, 0, false);
    }

    // 問題は「発行時刻から ttl 秒以内」、通行証は「期限より前」を有効とする。
    private boolean valid(String kind, String ip, String value, long ttlSeconds, boolean issuedAtValue) {
        if (value == null) {
            return false;
        }
        int dot = value.indexOf('.');
        if (dot <= 0 || dot == value.length() - 1) {
            return false;
        }
        long time;
        try {
            time = Long.parseLong(value.substring(0, dot));
        } catch (NumberFormatException e) {
            return false;
        }
        long now = clock.instant().getEpochSecond();
        boolean inTime = issuedAtValue ? now >= time && now - time <= ttlSeconds : now < time;
        String expected = sign(kind, ip, time);
        // 署名の比較は時間差で中身が漏れないよう、一定時間で比べる。
        return inTime && expected != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                value.substring(dot + 1).getBytes(StandardCharsets.US_ASCII));
    }

    private String sign(String kind, String ip, long time) {
        Optional<String> range = ipRange(ip);
        if (range.isEmpty()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal((kind + "|" + range.get() + "|" + time).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 通行証を結びつけるIPの範囲。携帯回線などで末尾が変わるたびに確かめ直しにならないよう、
     * IPv4は/24、IPv6は/64にまとめる。IPの形でない値は空(通行証を出さない)。
     */
    static Optional<String> ipRange(String ip) {
        if (ip == null) {
            return Optional.empty();
        }
        if (IPV4.matcher(ip).matches()) {
            String[] octets = ip.split("\\.");
            StringBuilder range = new StringBuilder("v4:");
            for (int i = 0; i < 3; i++) {
                int octet = Integer.parseInt(octets[i]);
                if (octet > 255) {
                    return Optional.empty();
                }
                range.append(octet).append('.');
            }
            return Integer.parseInt(octets[3]) > 255 ? Optional.empty() : Optional.of(range.toString());
        }
        // コロンを含む文字列は、InetAddressがIPv6の表記として読むだけでDNSには問い合わせない。
        if (!ip.contains(":") || !IPV6_CHARS.matcher(ip).matches()) {
            return Optional.empty();
        }
        try {
            byte[] address = InetAddress.getByName(ip).getAddress();
            if (address.length == 4) {
                // ::ffff:1.2.3.4 の形はIPv4として返ってくる。
                return ipRange((address[0] & 0xff) + "." + (address[1] & 0xff) + "." + (address[2] & 0xff) + "." + (address[3] & 0xff));
            }
            StringBuilder range = new StringBuilder("v6:");
            for (int i = 0; i < 8; i++) {
                range.append(String.format("%02x", address[i]));
            }
            return Optional.of(range.toString());
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }

    static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static int leadingZeroBits(byte[] digest) {
        int bits = 0;
        for (byte b : digest) {
            if (b == 0) {
                bits += 8;
                continue;
            }
            return bits + Integer.numberOfLeadingZeros(b & 0xff) - 24;
        }
        return bits;
    }
}
