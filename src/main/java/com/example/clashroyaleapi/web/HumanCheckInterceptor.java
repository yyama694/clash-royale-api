package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.domain.HumanPass;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * プレイヤー・クランの画面を、通行証(Cookie {@value #COOKIE})を持つ閲覧者にだけ出す(正体を隠したボット対策。2026-11-12)。
 *
 * 通行証が無いか、別のIPの範囲のものなら、公式APIもテンプレートも使わずに確かめ用のページ(403)を返す。
 * そのページのJSが計算を解いて {@code POST /human-check}(HumanCheckController)で通行証を受け取り、読み直す。
 * 人は一瞬の読み直しで次へ進み、JSを動かさないボットはここで止まる。
 *
 * 計算を解くボットが現れたので(2026-11-13)、解いた閲覧者にはすぐ通行証を出さず、ボタンのページを返す(2026-11-17)。
 * 押すと {@code POST /human-check/press} で通行証に替わる。確かめ用のページとその効き目はそのままで、解いた後に
 * 人の操作を1回はさむ。押す操作も自動化はできるが、このサイト専用の作り込みが要る。
 *
 * 名乗っているクローラー・リンクのプレビュー・自分たちのツール(curl・fact-check・ranking-live)は通す(検索結果やXのカードのため)。
 * IPはApacheが付けた X-Forwarded-For から取る(forward-headers-strategy: framework)。Apacheは外から来た同じヘッダーを
 * 消してから付けるので、偽れない(運用.md)。
 */
public class HumanCheckInterceptor implements HandlerInterceptor {

    static final String COOKIE = "pt";
    private static final Pattern DECLARED = Pattern.compile(
            "bot|crawl|spider|slurp|google|facebookexternalhit|meta-external|chatgpt-user|perplexity|claude-|whatsapp|yeti"
                    + "|^curl/|princess-tower-|ranking-live/",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MOBILE = Pattern.compile("iPhone|iPad|Android");

    private static final Pattern MESSAGE = Pattern.compile("\\{\\{([A-Za-z.]+)}}");

    private final HumanPass humanPass;
    private final boolean exemptMobile;
    private final String page;
    private final Map<String, String> buttonPages;

    /**
     * {@code page} は確かめ用のページのHTML。{@code __CHALLENGE__} と {@code __DIFFICULTY__} を差し替えて返す。
     * {@code buttonPages} は言語ごとのボタンのページ({@link #buttonPages})。空ならボタンの段を置かない。
     */
    public HumanCheckInterceptor(HumanPass humanPass, boolean exemptMobile, String page, Map<String, String> buttonPages) {
        this.humanPass = humanPass;
        this.exemptMobile = exemptMobile;
        this.page = page;
        this.buttonPages = buttonPages;
    }

    /**
     * ボタンのページを対応言語ごとに作っておく(解くボットにも返すので、リクエストのたびに文言を引かない)。
     * {@code template} の {@code {{キー}}} を文言に、{@code __LANG__} を言語コードに差し替える。
     */
    public static Map<String, String> buttonPages(String template, MessageSource messages) {
        Map<String, String> pages = new HashMap<>();
        for (Locale locale : SupportedLanguages.SUPPORTED) {
            String localized = MESSAGE.matcher(template.replace("__LANG__", locale.getLanguage())).replaceAll(key ->
                    Matcher.quoteReplacement(HtmlUtils.htmlEscape(messages.getMessage(key.group(1), null, locale), "UTF-8")));
            pages.put(locale.getLanguage(), localized);
        }
        return Map.copyOf(pages);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
            return true;
        }
        if (isExempt(request.getHeader(HttpHeaders.USER_AGENT), exemptMobile)) {
            return true;
        }
        String ip = request.getRemoteAddr();
        Cookie pass = WebUtils.getCookie(request, COOKIE);
        if (pass != null && humanPass.isValidPass(ip, pass.getValue())) {
            return true;
        }
        if (pass != null && !buttonPages.isEmpty() && humanPass.isAwaitingPress(ip, pass.getValue())) {
            String language = RequestContextUtils.getLocale(request).getLanguage();
            write(response, buttonPages.getOrDefault(language, buttonPages.get(SupportedLanguages.INTERNATIONAL.getLanguage())));
            return false;
        }
        Optional<String> challenge = humanPass.challenge(ip);
        if (challenge.isEmpty()) {
            // IPの形でない(本番では起きない)。通行証を出せないので、締め出さずに通す。
            return true;
        }
        write(response, page
                .replace("__CHALLENGE__", challenge.get())
                .replace("__DIFFICULTY__", Integer.toString(humanPass.difficulty())));
        return false;
    }

    private static void write(HttpServletResponse response, String html) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("X-Robots-Tag", "noindex");
        response.getWriter().write(html);
    }

    static boolean isExempt(String userAgent, boolean exemptMobile) {
        if (userAgent == null || userAgent.isEmpty()) {
            return false;
        }
        return DECLARED.matcher(userAgent).find() || (exemptMobile && MOBILE.matcher(userAgent).find());
    }
}
