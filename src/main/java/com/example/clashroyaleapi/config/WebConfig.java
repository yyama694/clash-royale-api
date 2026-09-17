package com.example.clashroyaleapi.config;

import com.example.clashroyaleapi.web.WebConstants;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.Locale;

/**
 * 表示言語は ?lang=ja / ?lang=en で切り替え、Cookieに保持する。
 * 未選択の訪問者にはAccept-Languageの判定を使う。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver(WebConstants.LANGUAGE_PARAM);
        // Accept-Languageが無いリクエスト(curl等)では、サーバーのOSロケールに左右されると
        // ローカルと本番で表示言語が変わってしまう。主対象である日本語に固定する。
        resolver.setDefaultLocaleFunction(request -> {
            String acceptLanguage = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
            return acceptLanguage == null || acceptLanguage.isBlank() ? Locale.JAPANESE : request.getLocale();
        });
        return resolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName(WebConstants.LANGUAGE_PARAM);
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
