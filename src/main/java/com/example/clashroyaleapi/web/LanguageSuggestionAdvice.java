package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.web.view.LanguageSuggestionView;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Arrays;
import java.util.Locale;

/**
 * ?lang= で着地した人に、その人の言語で開き直すリンクを出す(SupportedLanguages#suggestion)。
 * 自動では転送しない。Googleは言語による自動転送を勧めておらず、検索エンジンが各言語のページを見られなくなるため。
 * 文言は今の表示言語ではなく勧める言語で書く(今の言語が読めない人に向けたものなので)。
 * リンクは言語メニューと同じ ?setlang= にする(テンプレート側)。選んだ言語を保存し、以後の画面もその言語で出すため。
 */
@ControllerAdvice
public class LanguageSuggestionAdvice {

    private final MessageSource messageSource;

    public LanguageSuggestionAdvice(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ModelAttribute("languageSuggestion")
    public LanguageSuggestionView languageSuggestion(HttpServletRequest request, Locale locale) {
        // 1つの言語でしか作っていない画面は、他の言語で開き直しても同じ言語になるので勧めない。
        if (SupportedLanguages.fixedLanguage(request.getRequestURI()).isPresent()) {
            return null;
        }
        return SupportedLanguages.suggestion(locale, request.getParameter(WebConstants.LANGUAGE_PARAM),
                        savedLanguage(request), request.getHeader(HttpHeaders.ACCEPT_LANGUAGE))
                .map(suggested -> new LanguageSuggestionView(suggested.getLanguage(),
                        messageSource.getMessage("langSuggestion.view", null, suggested)))
                .orElse(null);
    }

    private static String savedLanguage(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> cookie.getName().equals(WebConstants.LANGUAGE_PARAM))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
