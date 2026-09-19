package com.example.clashroyaleapi.web;

import org.springframework.http.HttpHeaders;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 画面の表示言語として対応している言語の一覧と、要求された言語をそのどれかに寄せる規則。
 * 言語を追加するときは SUPPORTED と messages_xx.properties を足す。
 */
public final class SupportedLanguages {

    // 対応していない言語(fr等)の訪問者と、言語の希望が無い訪問者(検索エンジンのクローラー等)に出す言語。
    // 全世界向けのサービスなので、最も多くの人が読める英語にする。
    public static final Locale INTERNATIONAL = Locale.ENGLISH;

    // ポルトガル語の訳はブラジル向け(ゲーム内と同じ)だが、pt-PTの訪問者にも英語より読みやすいので言語だけで照合する。
    public static final List<Locale> SUPPORTED = List.of(Locale.JAPANESE, Locale.ENGLISH,
            Locale.forLanguageTag("es"), Locale.forLanguageTag("pt"));

    /** 言語切替リンクとhreflangに使う言語コードの一覧。 */
    public static List<String> languageCodes() {
        return SUPPORTED.stream().map(Locale::getLanguage).toList();
    }

    private SupportedLanguages() {
    }

    /** ja-JP のような地域付きの指定も言語だけで照合する。対応していなければ空。 */
    public static Optional<Locale> match(Locale requested) {
        if (requested == null) {
            return Optional.empty();
        }
        return SUPPORTED.stream()
                .filter(supported -> supported.getLanguage().equals(requested.getLanguage()))
                .findFirst();
    }

    /**
     * Accept-Languageの候補をq値の高い順に見て、最初に対応している言語を選ぶ。
     * request.getLocale() は先頭の1言語しか見ないため、fr,ja;q=0.9 で日本語を選べない。
     */
    public static Locale fromAcceptLanguage(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
        if (header == null || header.isBlank()) {
            return INTERNATIONAL;
        }
        try {
            return Locale.LanguageRange.parse(header).stream()
                    .map(range -> match(Locale.forLanguageTag(range.getRange())))
                    .flatMap(Optional::stream)
                    .findFirst()
                    .orElse(INTERNATIONAL);
        } catch (IllegalArgumentException e) {
            // 壊れたAccept-Languageは「希望なし」と同じ扱いにする。
            return INTERNATIONAL;
        }
    }

    /** 画面・国名・日時の表示に使うロケール。Controllerが受け取るLocaleは解決済みだが、念のため対応言語に寄せる。 */
    public static Locale displayLocale(Locale locale) {
        return match(locale).orElse(INTERNATIONAL);
    }
}
