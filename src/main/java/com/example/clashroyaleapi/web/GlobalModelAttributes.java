package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.config.PlayerIndexProperties;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 言語切替リンクが「今見ているページのまま言語だけ切り替える」ために、
 * 現在のURI(既存のクエリ付き、langとfromは除く)を全テンプレートに渡す。
 */
@ControllerAdvice
public class GlobalModelAttributes {

    private final boolean nameSearchEnabled;

    public GlobalModelAttributes(PlayerIndexProperties playerIndexProperties) {
        this.nameSearchEnabled = playerIndexProperties.nameSearchEnabled();
    }

    /** プレイヤー検索フォームの文言を「タグ」か「名前またはタグ」で切り替えるため。 */
    @ModelAttribute("nameSearchEnabled")
    public boolean nameSearchEnabled() {
        return nameSearchEnabled;
    }

    /**
     * 流入元タグ(from)は最初の表示のビーコンで数え終わっているので、言語切替のリンクにも、
     * hreflang・正規URLなど検索エンジン向けのURLにも引き継がない。
     */
    @ModelAttribute("currentUri")
    public String currentUri(HttpServletRequest request) {
        String errorUri = forwardedFromError(request);
        String uri = errorUri != null ? errorUri : request.getRequestURI();
        // クエリはエラー転送でも元のリクエストのものが残るため、パスだけ差し替えれば足りる。
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return uri;
        }
        String kept = Arrays.stream(query.split("&"))
                .filter(param -> !param.startsWith(WebConstants.LANGUAGE_PARAM + "="))
                .filter(param -> !param.startsWith(WebConstants.SOURCE_PARAM + "="))
                .collect(Collectors.joining("&"));
        return kept.isEmpty() ? uri : uri + "?" + kept;
    }

    /**
     * 検索エンジンに示す正規URL(canonical)。?from=x 付きのURLがXなどに貼られて巡回されても、
     * 評価が別々のURLに割れないよう、fromを除いたURLにする。言語はhreflangの各言語のURLに合わせ、
     * 対応言語のlangが指定されていれば残す(無ければx-defaultのURL)。エラー画面には出さないので null。
     */
    @ModelAttribute("canonicalUrl")
    public String canonicalUrl(HttpServletRequest request) {
        if (forwardedFromError(request) != null) {
            return null;
        }
        String url = siteBaseUrl(request) + currentUri(request);
        String lang = request.getParameter(WebConstants.LANGUAGE_PARAM);
        if (lang == null || !SupportedLanguages.languageCodes().contains(lang)) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + WebConstants.LANGUAGE_PARAM + "=" + lang;
    }

    @ModelAttribute("languageCodes")
    public List<String> languageCodes() {
        return SupportedLanguages.languageCodes();
    }

    /**
     * hreflang は検索エンジン向けの指定で、絶対URLでないと解釈されない。
     * 独自ドメインを取るまではアクセスされたホストをそのまま使う。
     */
    @ModelAttribute("siteBaseUrl")
    public String siteBaseUrl(HttpServletRequest request) {
        return ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath(null)
                .replaceQuery(null)
                .build()
                .toUriString();
    }

    /**
     * エラー画面はサーブレットコンテナが /error に転送して描画するため、getRequestURI() は /error になる。
     * それだと言語切替リンクの行き先が /error?lang=... になってしまうので、転送前のURLを使う。
     */
    private String forwardedFromError(HttpServletRequest request) {
        String uri = asString(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));
        return uri == null || uri.isBlank() ? null : uri;
    }

    private String asString(Object value) {
        return value instanceof String text ? text : null;
    }
}
