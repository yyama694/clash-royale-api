package com.example.clashroyaleapi.web;

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
 * 現在のURI(既存のクエリ付き、langは除く)を全テンプレートに渡す。
 */
@ControllerAdvice
public class GlobalModelAttributes {

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
                .collect(Collectors.joining("&"));
        return kept.isEmpty() ? uri : uri + "?" + kept;
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
