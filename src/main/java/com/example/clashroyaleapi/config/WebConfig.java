package com.example.clashroyaleapi.config;

import com.example.clashroyaleapi.web.CrossSiteRequestGuard;
import com.example.clashroyaleapi.web.GlobalModelAttributes;
import com.example.clashroyaleapi.web.LanguageInterceptor;
import com.example.clashroyaleapi.web.SupportedLanguages;
import com.example.clashroyaleapi.web.WebConstants;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.SimpleLocaleContext;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.resource.VersionResourceResolver;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * 表示言語は ?lang=ja / ?lang=es でその画面だけ切り替え、言語メニューで選んだ言語(?setlang=)はCookieに保持する
 * (LanguageInterceptor)。どちらも無い訪問者にはAccept-Languageから対応言語を選ぶ。
 * 対応言語の一覧は SupportedLanguages に集約している。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    // 国別ランキングの国選択Cookieと揃える。ブラウザを閉じるたびに言語選択が消えないようにするため。
    private static final Duration LANGUAGE_COOKIE_MAX_AGE = Duration.ofDays(365);

    private static final Duration STATIC_CACHE_MAX_AGE = Duration.ofDays(365);

    private final GlobalModelAttributes modelAttributes;

    public WebConfig(GlobalModelAttributes modelAttributes) {
        this.modelAttributes = modelAttributes;
    }

    /** 表示言語は ?lang= > Cookie(言語メニューで選んだ言語) > Accept-Language > 英語 の順に決める。 */
    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver(WebConstants.LANGUAGE_PARAM) {
            // 以前のバージョンが保存した lang=fr のような対応外の値は、無かったものとして扱う。
            @Override
            protected Locale parseLocaleValue(String value) {
                return SupportedLanguages.match(super.parseLocaleValue(value)).orElse(null);
            }

            // ?lang= はその画面の表示にだけ使い、Cookieには書かない(保存は LanguageInterceptor の ?setlang=)。
            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                return requestedLanguage(request).orElseGet(() -> super.resolveLocale(request));
            }

            @Override
            public LocaleContext resolveLocaleContext(HttpServletRequest request) {
                return requestedLanguage(request).<LocaleContext>map(SimpleLocaleContext::new)
                        .orElseGet(() -> super.resolveLocaleContext(request));
            }
        };
        resolver.setCookieMaxAge(LANGUAGE_COOKIE_MAX_AGE);
        // 既定では壊れたCookie値で例外(500)になる。言語の選択が壊れていても画面は出したいので無視させる。
        resolver.setRejectInvalidCookies(false);
        resolver.setDefaultLocaleFunction(SupportedLanguages::fromAcceptLanguage);
        return resolver;
    }

    // 対応外の言語・壊れた値は指定が無かったものとして扱う(Cookie・Accept-Languageで決まる)。
    private static Optional<Locale> requestedLanguage(HttpServletRequest request) {
        return SupportedLanguages.fromParameter(request.getParameter(WebConstants.LANGUAGE_PARAM));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LanguageInterceptor(localeResolver(), modelAttributes));
        registry.addInterceptor(new CrossSiteRequestGuard()).addPathPatterns("/favorites/**");
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
