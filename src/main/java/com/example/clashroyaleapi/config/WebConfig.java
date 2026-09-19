package com.example.clashroyaleapi.config;

import com.example.clashroyaleapi.web.SupportedLanguages;
import com.example.clashroyaleapi.web.WebConstants;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.time.Duration;
import java.util.Locale;

/**
 * 表示言語は ?lang=ja / ?lang=es のように切り替え、Cookieに保持する。
 * 未選択の訪問者にはAccept-Languageから対応言語を選ぶ。対応言語の一覧は SupportedLanguages に集約している。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    // 国別ランキングの国選択Cookieと揃える。ブラウザを閉じるたびに言語選択が消えないようにするため。
    private static final Duration LANGUAGE_COOKIE_MAX_AGE = Duration.ofDays(365);

    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver(WebConstants.LANGUAGE_PARAM) {
            // 以前のバージョンが保存した lang=fr のような対応外の値は、無かったものとして扱う。
            @Override
            protected Locale parseLocaleValue(String value) {
                return SupportedLanguages.match(super.parseLocaleValue(value)).orElse(null);
            }
        };
        resolver.setCookieMaxAge(LANGUAGE_COOKIE_MAX_AGE);
        // 既定では壊れたCookie値で例外(500)になる。言語の選択が壊れていても画面は出したいので無視させる。
        resolver.setRejectInvalidCookies(false);
        resolver.setDefaultLocaleFunction(SupportedLanguages::fromAcceptLanguage);
        return resolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor() {
            // 対応外の言語・壊れた値は例外にし、setIgnoreInvalidLocale で黙って無視させる
            // (既定では500になり、エラー画面への転送でも同じ例外が出てTomcatの素のエラーページになる)。
            @Override
            protected Locale parseLocaleValue(String localeValue) {
                return SupportedLanguages.match(super.parseLocaleValue(localeValue))
                        .orElseThrow(() -> new IllegalArgumentException("unsupported language: " + localeValue));
            }

            // 同じURLでもCookieとAccept-Languageで返す言語が変わるため、共有キャッシュに言語違いを混ぜさせない。
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws jakarta.servlet.ServletException {
                // エラー画面への転送でもう一度通るため、重複して付けない。
                if (!response.getHeaders(HttpHeaders.VARY).contains(HttpHeaders.ACCEPT_LANGUAGE)) {
                    response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE);
                    response.addHeader(HttpHeaders.VARY, HttpHeaders.COOKIE);
                }
                return super.preHandle(request, response, handler);
            }
        };
        interceptor.setParamName(WebConstants.LANGUAGE_PARAM);
        interceptor.setIgnoreInvalidLocale(true);
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
