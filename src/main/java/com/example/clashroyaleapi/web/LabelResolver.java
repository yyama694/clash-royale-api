package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.domain.ClanRole;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 公式APIが返す英語の生値(カード名・ゲームモード名・役職名)を表示用ラベルに解決する。
 * 対応表はメッセージリソース(messages_ja.properties 等)側にあり、未登録のキーは生値のままフォールバックする。
 * 新カードが追加されてもコード変更なしに表示できるのが狙い。
 */
@Component
public class LabelResolver {

    private static final String EMPTY = "-";

    private final MessageSource messageSource;

    public LabelResolver(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String cardName(String rawName, Locale locale) {
        return resolve("card.", rawName, locale);
    }

    public String gameMode(String rawName, Locale locale) {
        return resolve("gamemode.", rawName, locale);
    }

    public String role(String rawRole, Locale locale) {
        if (isEmpty(rawRole)) {
            return EMPTY;
        }
        // 対応表にない役職(APIに新役職が増えた場合)は生値をそのまま出す。
        return ClanRole.from(rawRole)
                .map(role -> messageSource.getMessage(role.messageKey(), null, rawRole, locale))
                .orElse(rawRole);
    }

    public String message(String key, Locale locale) {
        return messageSource.getMessage(key, null, key, locale);
    }

    private String resolve(String prefix, String rawName, Locale locale) {
        if (isEmpty(rawName)) {
            return EMPTY;
        }
        return messageSource.getMessage(prefix + rawName, null, rawName, locale);
    }

    private boolean isEmpty(String value) {
        return value == null || value.isBlank();
    }
}
