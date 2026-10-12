package com.example.clashroyaleapi.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 「これは自分」で登録したプレイヤーのタグ(先頭の"#"なし)のCookie。トップページの一番上に、その人の今の成績を出すために使う。
 * 値はタグだけ(名前は持たない。トップページは公式APIから今の名前を取る)。形式に合わない値は無かったものとして扱う
 * (Cookieは利用者側で書き換えられるが、影響はその人の画面だけのため)。
 */
@Component
public class MeCookie {

    static final String NAME = "me";
    private static final Duration MAX_AGE = Duration.ofDays(365);
    private static final Pattern TAG = Pattern.compile("[0-9A-Z]{3,15}");

    public Optional<String> read(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, NAME);
        return Optional.ofNullable(cookie).map(Cookie::getValue).filter(value -> TAG.matcher(value).matches());
    }

    public void write(HttpServletResponse response, String pathTag) {
        response.addCookie(cookie(pathTag, (int) MAX_AGE.toSeconds()));
    }

    public void clear(HttpServletResponse response) {
        response.addCookie(cookie("", 0));
    }

    // お気に入りのCookieと同じ設定。CSRF対策の本体は CrossSiteRequestGuard にある(SameSite=Laxだけでは足りない)。
    private static Cookie cookie(String value, int maxAge) {
        Cookie cookie = new Cookie(NAME, value);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        cookie.setHttpOnly(true);
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }
}
