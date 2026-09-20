package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.domain.Favorites;
import com.example.clashroyaleapi.domain.GameText;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * お気に入り(プレイヤー・クラン)のCookieの読み書き。
 * 値は "1." + Base64URL(パディングなし) で、中身はUTF-8の「タグ(#なし) TAB 名前」を改行でつないだもの(登録が新しい順)。
 * 先頭の "1." は形式のバージョン。読み込みは壊れた値に寛容にし、おかしければその行(または全体)を無視して空扱いにする
 * (Cookieは利用者側で書き換えられるが、影響はその人の画面だけのため)。
 */
@Component
public class FavoriteCookies {

    private static final String VERSION_PREFIX = "1.";
    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(365);
    private static final int MAX_NAME_LENGTH = 50;
    private static final Pattern TAG_LINE = Pattern.compile("[0-9A-Z]{3,15}");

    public Favorites read(HttpServletRequest request, FavoriteKind kind) {
        Cookie cookie = WebUtils.getCookie(request, kind.cookieName());
        if (cookie == null) {
            return Favorites.empty();
        }
        return decode(cookie.getValue());
    }

    /** プレイヤー情報画面・クラン情報画面を開いたときに呼ぶ。登録済みで保存名と違えばCookieを書き直す。 */
    public Favorites refreshName(HttpServletRequest request, HttpServletResponse response, FavoriteKind kind,
            String tag, String name) {
        Favorites favorites = read(request, kind);
        Favorites updated = favorites.updateName(tag, name);
        if (updated != favorites) {
            write(response, kind, updated);
        }
        return updated;
    }

    public void write(HttpServletResponse response, FavoriteKind kind, Favorites favorites) {
        Cookie cookie = new Cookie(kind.cookieName(), encode(favorites));
        cookie.setPath("/");
        cookie.setMaxAge((int) COOKIE_MAX_AGE.toSeconds());
        cookie.setHttpOnly(true);
        // 他サイトからのPOSTで勝手に登録・解除されない(CSRF)よう、初回遷移以外は送らないLaxにする。
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    private Favorites decode(String value) {
        if (value == null || !value.startsWith(VERSION_PREFIX)) {
            return Favorites.empty();
        }
        String body;
        try {
            body = new String(Base64.getUrlDecoder().decode(value.substring(VERSION_PREFIX.length())),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Favorites.empty();
        }
        if (body.isEmpty()) {
            return Favorites.empty();
        }
        List<Favorites.Entry> entries = body.lines()
                .map(this::toEntry)
                .filter(Objects::nonNull)
                .toList();
        return Favorites.of(entries);
    }

    private Favorites.Entry toEntry(String line) {
        int tab = line.indexOf('\t');
        if (tab < 0) {
            return null;
        }
        String tag = line.substring(0, tab);
        String name = line.substring(tab + 1);
        if (!TAG_LINE.matcher(tag).matches() || name.isEmpty()) {
            return null;
        }
        String stripped = GameText.stripFormatting(name);
        String trimmed = stripped.length() > MAX_NAME_LENGTH ? stripped.substring(0, MAX_NAME_LENGTH) : stripped;
        return new Favorites.Entry(tag, trimmed);
    }

    private String encode(Favorites favorites) {
        String body = favorites.entries().stream()
                .map(e -> e.tag() + "\t" + e.name())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        return VERSION_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(body.getBytes(StandardCharsets.UTF_8));
    }
}
