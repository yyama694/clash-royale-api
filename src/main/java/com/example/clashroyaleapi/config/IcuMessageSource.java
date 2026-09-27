package com.example.clashroyaleapi.config;

import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.util.ULocale;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.util.ObjectUtils;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 引数付きメッセージの書式にICUのMessageFormatを使う。
 * JDKのMessageFormatには言語ごとの複数形の規則が無く、"1 crowns" のような表記を避けるには
 * テンプレート側で数値を分岐するしかなかった。ICUなら {0, plural, one {# crown} other {# crowns}} と
 * メッセージ側に書けるので、複数形が3種類以上ある言語を足してもテンプレートを変えずに済む。
 */
public class IcuMessageSource extends ResourceBundleMessageSource {

    // 書式の解析と複数形の規則の取得は重いため、書式と言語の組ごとに1回だけ作って使い回す
    // (個人ランキング画面は1回の描画で1000回以上呼ぶ)。書式はメッセージファイルにあるものだけなので、
    // 件数は「引数付きのメッセージの数 × 言語の数」で頭打ちになる。
    private final ConcurrentMap<FormatKey, MessageFormat> formats = new ConcurrentHashMap<>();

    public static IcuMessageSource forBasename(String basename) {
        IcuMessageSource messageSource = new IcuMessageSource();
        messageSource.setBasename(basename);
        messageSource.setDefaultEncoding("UTF-8");
        // OSの既定ロケールがja_JPのため、falseにしないと英語表示の要求まで日本語にフォールバックしてしまう。
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }

    @Override
    protected String getMessageInternal(String code, Object[] args, Locale locale) {
        if (code == null || ObjectUtils.isEmpty(args)) {
            return super.getMessageInternal(code, args, locale);
        }
        Locale target = locale != null ? locale : Locale.getDefault();
        String pattern = resolveCodeWithoutArguments(code, target);
        if (pattern == null) {
            return super.getMessageInternal(code, args, locale);
        }
        MessageFormat format = formats.computeIfAbsent(new FormatKey(pattern, target),
                key -> new MessageFormat(key.pattern(), ULocale.forLocale(key.locale())));
        Object[] arguments = resolveArguments(args, target);
        // ICUのMessageFormatはスレッドセーフではないため、使い回す以上は書式ごとに同期する。
        synchronized (format) {
            return format.format(arguments);
        }
    }

    private record FormatKey(String pattern, Locale locale) {
    }
}
