package com.example.clashroyaleapi.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Set;

/**
 * 他サイトから送られてきた、状態を変えるリクエスト(お気に入りの登録・解除のPOST)を拒否する(CSRF対策)。
 *
 * お気に入りのCookieは SameSite=Lax だが、Lax が止めるのはCookieの送信だけで、他サイトから画面ごと遷移してきた
 * POSTへの応答の Set-Cookie はブラウザに保存される。サーバーはCookieが無い=空の一覧として読んで書き戻すため、
 * 他サイトにフォームを自動送信させるだけで、一覧を消したり攻撃者の選んだ1件に置き換えたりできてしまう。
 *
 * 判定はブラウザが付ける Sec-Fetch-Site を優先し、それを送らない古いブラウザでは Origin を自分のオリジンと比べる。
 * どちらも無いリクエストはブラウザ以外からのもので、閲覧者のCookieを持たないため通す。
 */
public class CrossSiteRequestGuard implements HandlerInterceptor {

    private static final String SEC_FETCH_SITE = "Sec-Fetch-Site";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return true;
        }
        String fetchSite = request.getHeader(SEC_FETCH_SITE);
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (isCrossSite(fetchSite, origin, ownOrigin(request))) {
            throw new CrossSiteRequestException(request.getMethod() + " " + request.getRequestURI()
                    + " (" + SEC_FETCH_SITE + ": " + fetchSite + ", Origin: " + origin + ")");
        }
        return true;
    }

    static boolean isCrossSite(String fetchSite, String origin, String ownOrigin) {
        if (fetchSite != null) {
            // same-site(同じサイトの別オリジン)も拒否する。このサイトのフォームやfetchは同じオリジンからしか送られない。
            // none は利用者自身の操作(アドレスバーなど)で、他サイトからは作れない。
            return !fetchSite.equals("same-origin") && !fetchSite.equals("none");
        }
        return origin != null && !origin.equalsIgnoreCase(ownOrigin);
    }

    // ApacheからのX-Forwarded-*を反映済みの値(forward-headers-strategy: framework)なので、本番でも https://ドメイン になる。
    private static String ownOrigin(HttpServletRequest request) {
        return ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath(null)
                .replaceQuery(null)
                .toUriString();
    }
}
