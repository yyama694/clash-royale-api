package com.example.clashroyaleapi.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * ?lang=xx で開いた画面では、sitemapに載せている画面(カード・デッキ・ランキング・バランス調整など)へのリンクにも
 * ?lang=xx を付ける。サイト内のリンクは言語の無いURLで、言語メニューは setlang(nofollow)なので、検索エンジンは
 * 各言語の画面をhreflangとsitemapでしか知れない。Googleはsitemapを読まず、2026-11-28に解説を載せたカード詳細の
 * 日本語・英語の版が「Googleに認識されていません」だった。
 * プレイヤー・クランの画面には付けない。たどれる言語の版を増やすと、巡回と公式APIの呼び出しが増えるため
 * (2026-11-28にユーザーが決定)。
 * テンプレートの @{...} のリンクは response.encodeURL を通るので、そこで足す。テンプレートで lang=${null} と書くと
 * 「?lang=」が残り、言語の無いURLと別のURLになってしまう。
 */
@Component
public class LanguageLinkFilter extends OncePerRequestFilter {

    // パスの最初の区切り。"" はトップページ。
    private static final Set<String> SECTIONS = Set.of("", "cards", "card", "decks", "pros", "ranking", "balance",
            "quiz", "about", "privacy");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Locale> language = SupportedLanguages.fromParameter(
                request.getParameter(WebConstants.LANGUAGE_PARAM));
        if (language.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        String code = language.get().getLanguage();
        chain.doFilter(request, new HttpServletResponseWrapper(response) {
            @Override
            public String encodeURL(String url) {
                return super.encodeURL(withLanguage(url, code));
            }
        });
    }

    /**
     * サイト内のパス(/ で始まるもの)だけに付ける。hreflang・x-defaultは絶対URLで作っていて、言語を足すと
     * 中身と食い違うので触らない。言語を指定済みのリンク(lang・setlang)もそのまま。
     */
    static String withLanguage(String url, String code) {
        if (url == null || !url.startsWith("/") || url.startsWith("//")) {
            return url;
        }
        int hash = url.indexOf('#');
        String beforeFragment = hash < 0 ? url : url.substring(0, hash);
        String fragment = hash < 0 ? "" : url.substring(hash);
        int question = beforeFragment.indexOf('?');
        String path = question < 0 ? beforeFragment : beforeFragment.substring(0, question);
        String query = question < 0 ? "" : beforeFragment.substring(question + 1);
        if (!SECTIONS.contains(firstSegment(path)) || hasLanguage(query)) {
            return url;
        }
        String separator = question < 0 ? "?" : query.isEmpty() ? "" : "&";
        return beforeFragment + separator + WebConstants.LANGUAGE_PARAM + "=" + code + fragment;
    }

    private static String firstSegment(String path) {
        int end = path.indexOf('/', 1);
        return end < 0 ? path.substring(1) : path.substring(1, end);
    }

    private static boolean hasLanguage(String query) {
        return Arrays.stream(query.split("&"))
                .map(param -> param.split("=", 2)[0])
                .anyMatch(name -> name.equals(WebConstants.LANGUAGE_PARAM)
                        || name.equals(WebConstants.SAVE_LANGUAGE_PARAM));
    }
}
