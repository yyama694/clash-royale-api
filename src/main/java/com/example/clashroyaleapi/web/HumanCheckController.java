package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.config.HumanCheckProperties;
import com.example.clashroyaleapi.domain.HumanPass;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 確かめ用のページのJSが解いた答えを受け取り、合っていれば通行証のCookieを付ける(HumanCheckInterceptor)。
 * ボタンの段がある間(human-check.button)は、答えには「ボタン待ち」の印を付け、ボタンが押されてから通行証に替える。
 * Cookieを変えるPOSTなので、他サイトからの送信は CrossSiteRequestGuard で止める。
 */
@RestController
public class HumanCheckController {

    private static final Pattern WAITED = Pattern.compile("\\d{1,7}");

    private final HumanPass humanPass;
    private final boolean button;

    public HumanCheckController(HumanPass humanPass, HumanCheckProperties properties) {
        this.humanPass = humanPass;
        this.button = properties.button();
    }

    @PostMapping("/human-check")
    public ResponseEntity<Void> solve(@RequestParam(name = "c", required = false) String challenge,
            @RequestParam(name = "a", required = false) String answer,
            HttpServletRequest request, HttpServletResponse response) {
        String ip = request.getRemoteAddr();
        return button
                ? respond(humanPass.solveAwaitingPress(ip, challenge, answer), HumanPass.PRESS_TTL, request, response)
                : respond(humanPass.solve(ip, challenge, answer), HumanPass.PASS_TTL, request, response);
    }

    /**
     * ボタンのページから送られる。{@code d} は表示してから押すまでのミリ秒で、Apacheのアクセスログに残して
     * 人の操作かどうかを後から見るために受け取る(access-check)。無ければボタンのページから来ていないので断る。
     */
    @PostMapping("/human-check/press")
    public ResponseEntity<Void> press(@RequestParam(name = "d", required = false) String waited,
            HttpServletRequest request, HttpServletResponse response) {
        Cookie awaitingPress = WebUtils.getCookie(request, HumanCheckInterceptor.COOKIE);
        Optional<String> pass = waited != null && WAITED.matcher(waited).matches() && awaitingPress != null
                ? humanPass.press(request.getRemoteAddr(), awaitingPress.getValue())
                : Optional.empty();
        return respond(pass, HumanPass.PASS_TTL, request, response);
    }

    private static ResponseEntity<Void> respond(Optional<String> value, Duration maxAge,
            HttpServletRequest request, HttpServletResponse response) {
        if (value.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
        }
        Cookie cookie = new Cookie(HumanCheckInterceptor.COOKIE, value.get());
        cookie.setPath("/");
        cookie.setMaxAge((int) maxAge.toSeconds());
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }
}
