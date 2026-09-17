package com.example.clashroyaleapi.config;

import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.util.ULocale;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.util.ObjectUtils;

import java.util.Locale;

/**
 * 引数付きメッセージの書式にICUのMessageFormatを使う。
 * JDKのMessageFormatには言語ごとの複数形の規則が無く、"1 crowns" のような表記を避けるには
 * テンプレート側で数値を分岐するしかなかった。ICUなら {0, plural, one {# crown} other {# crowns}} と
 * メッセージ側に書けるので、複数形が3種類以上ある言語を足してもテンプレートを変えずに済む。
 */
public class IcuMessageSource extends ResourceBundleMessageSource {

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
        return new MessageFormat(pattern, ULocale.forLocale(target)).format(resolveArguments(args, target));
    }
}
