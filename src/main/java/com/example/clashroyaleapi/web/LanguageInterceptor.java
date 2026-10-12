package com.example.clashroyaleapi.web;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import java.util.Locale;
import java.util.Optional;

/**
 * 言語メニューで選んだ言語(?setlang=xx)をCookieに保存する。
 *
 * ?lang=xx は「その画面を何語で出すか」だけを決め(WebConfig の LocaleResolver が読む)、Cookieには保存しない。
 * 共有したURL・Xに貼ったURL・検索結果のURLに lang を付けても、踏んだ人の言語の設定を上書きしないため。
 * 保存するのは言語メニューで選んだときだけで、保存したら ?lang=xx のURLへ転送する
 * (アドレスバーや、そこからコピーしたURLに setlang を残さないため)。
 *
 * 言語メニューは普通のリンクなので、検索エンジンがたどって setlang 付きのURLのまま検索結果に載せることがある
 * (2026-10に実際に載っていた)。そのURLを踏んだ人の設定を上書きしないよう、このサイトの画面から来たときだけ保存し、
 * 外から来たときは保存せずに ?lang=xx へ転送する。
 */
public class LanguageInterceptor implements HandlerInterceptor {

    private final LocaleResolver localeResolver;
    private final GlobalModelAttributes modelAttributes;

    public LanguageInterceptor(LocaleResolver localeResolver, GlobalModelAttributes modelAttributes) {
        this.localeResolver = localeResolver;
        this.modelAttributes = modelAttributes;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        addVary(response, handler);
        String requested = request.getParameter(WebConstants.SAVE_LANGUAGE_PARAM);
        if (requested == null || !HttpMethod.GET.matches(request.getMethod())) {
            return true;
        }
        Optional<Locale> language = SupportedLanguages.fromParameter(requested);
        if (isFromOwnPage(request.getHeader(CrossSiteRequestGuard.SEC_FETCH_SITE),
                request.getHeader(HttpHeaders.REFERER), CrossSiteRequestGuard.ownOrigin(request))) {
            language.ifPresent(locale -> localeResolver.setLocale(request, response, locale));
        }
        // 存在しないURLのエラー画面は、転送しても同じエラーになるだけなので、転送せずにそのまま描く。
        if (request.getDispatcherType() == DispatcherType.ERROR) {
            return true;
        }
        String uri = modelAttributes.currentUri(request);
        String target = language
                .map(locale -> uri + (uri.contains("?") ? "&" : "?") + WebConstants.LANGUAGE_PARAM + "="
                        + locale.getLanguage())
                .orElse(uri);
        response.setStatus(HttpServletResponse.SC_SEE_OTHER);
        response.setHeader(HttpHeaders.LOCATION, target);
        return false;
    }

    /**
     * このサイトの画面からのリンク(言語メニュー)で来たか。ブラウザが付ける Sec-Fetch-Site を優先し、
     * それを送らない古いブラウザでは Referer のオリジンで見る(Referrer-Policy は同じオリジンへは全体を送る設定)。
     * 検索エンジンのクローラーはどちらも送らないので、保存しない側になる。
     */
    static boolean isFromOwnPage(String fetchSite, String referer, String ownOrigin) {
        if (fetchSite != null) {
            return fetchSite.equals("same-origin");
        }
        return referer != null && (referer.equalsIgnoreCase(ownOrigin)
                || referer.regionMatches(true, 0, ownOrigin + "/", 0, ownOrigin.length() + 1));
    }

    /**
     * 同じURLでもCookieとAccept-Languageで返す言語が変わるため、共有キャッシュに言語違いを混ぜさせない。
     * CSS・画像は言語で内容が変わらない。Vary: Cookie を付けるとブラウザがお気に入りや言語のCookieが
     * 変わるたびに再取得してしまい、内容ハッシュ付きURLの長期キャッシュが効かなくなる。
     * エラー画面への転送でもう一度通るため、重複しても付けない。
     */
    private static void addVary(HttpServletResponse response, Object handler) {
        if (!(handler instanceof ResourceHttpRequestHandler)
                && !response.getHeaders(HttpHeaders.VARY).contains(HttpHeaders.ACCEPT_LANGUAGE)) {
            response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE);
            response.addHeader(HttpHeaders.VARY, HttpHeaders.COOKIE);
        }
    }
}
