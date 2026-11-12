package com.example.clashroyaleapi.web;

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

import java.util.Optional;

/**
 * 確かめ用のページのJSが解いた答えを受け取り、合っていれば通行証のCookieを付ける(HumanCheckInterceptor)。
 * Cookieを変えるPOSTなので、他サイトからの送信は CrossSiteRequestGuard で止める。
 */
@RestController
public class HumanCheckController {

    private final HumanPass humanPass;

    public HumanCheckController(HumanPass humanPass) {
        this.humanPass = humanPass;
    }

    @PostMapping("/human-check")
    public ResponseEntity<Void> solve(@RequestParam(name = "c", required = false) String challenge,
            @RequestParam(name = "a", required = false) String answer,
            HttpServletRequest request, HttpServletResponse response) {
        Optional<String> pass = humanPass.solve(request.getRemoteAddr(), challenge, answer);
        if (pass.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
        }
        Cookie cookie = new Cookie(HumanCheckInterceptor.COOKIE, pass.get());
        cookie.setPath("/");
        cookie.setMaxAge((int) HumanPass.PASS_TTL.toSeconds());
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }
}
