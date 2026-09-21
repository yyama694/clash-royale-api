package com.example.clashroyaleapi.config;

import com.example.clashroyaleapi.web.SupportedLanguages;
import com.example.clashroyaleapi.web.WebConstants;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import org.springframework.web.servlet.resource.VersionResourceResolver;

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

    private static final Duration STATIC_CACHE_MAX_AGE = Duration.ofDays(365);

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
                // CSS・画像は言語で内容が変わらない。Vary: Cookie を付けるとブラウザがお気に入りや言語のCookieが
                // 変わるたびに再取得してしまい、内容ハッシュ付きURLの長期キャッシュが効かなくなる。
                // エラー画面への転送でもう一度通るため、重複しても付けない。
                if (!(handler instanceof ResourceHttpRequestHandler)
                        && !response.getHeaders(HttpHeaders.VARY).contains(HttpHeaders.ACCEPT_LANGUAGE)) {
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

    /**
     * 静的ファイルの公開先を、実際に置いてある場所だけに絞る(Spring Bootの既定の `/**` は
     * `spring.web.resources.add-mappings=false` で止めている)。
     *
     * 既定の `/**` のままだと、テンプレートの `@{...}` で作るリンクが毎回
     * 「そのURLは静的ファイルではないか」と全URLについて問い合わせに行く(内容ハッシュ付きURLを
     * 作る仕組みが、リンク生成時に `ResourceUrlProvider` を通すため)。
     * 問い合わせはクラスパス上のファイル探索になり、JDKのクラスローダーが結果をURLごとに
     * 恒久的にキャッシュするため、`/player/<タグ>` のようにURLが無限に増えるリンクを出すたびに
     * ヒープが増え続ける。ランキング画面は1ページで千件以上のリンクを出すため影響が大きく、
     * 本番では17時間でヒープが約1GB埋まり、Full GCが常時走って表示が数秒止まっていた(2026-09-21に特定)。
     *
     * ここで実在する場所だけを登録すると、`/player/...` などはパターンに合わず即座に対象外と判断され、
     * ファイル探索もキャッシュへの登録も起きない。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        register(registry, "classpath:/static/css/", "/css/**");
        register(registry, "classpath:/static/images/", "/images/**");
        // ルート直下に置くもの。favicon と、Search Consoleの所有権確認ファイル。
        // どちらもパターンにしている(faviconは内容ハッシュ付きURLを作るのに * が要る。確認ファイルは名前が変わっても拾うため)。
        register(registry, "classpath:/static/", "/favicon*.png", "/google*.html");
    }

    private static void register(ResourceHandlerRegistry registry, String location, String... patterns) {
        registry.addResourceHandler(patterns)
                .addResourceLocations(location)
                // URLに内容のハッシュが入るので、同じURLの中身は変わらない。長期間キャッシュしてよい。
                .setCacheControl(CacheControl.maxAge(STATIC_CACHE_MAX_AGE))
                .resourceChain(true)
                .addResolver(new VersionResourceResolver().addContentVersionStrategy("/**"));
    }
}
