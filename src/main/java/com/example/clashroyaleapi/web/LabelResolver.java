package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.domain.ClanRole;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 公式APIが返す英語の生値(カード名・ゲームモード名・役職名)を表示用ラベルに解決する。
 * 対応表はメッセージリソース(messages_ja.properties 等)側にあり、新カードが追加されてもコード変更なしに辞書だけで足せる。
 * 辞書に無い値に初めて出会ったときはWARNログを出し、訳の追加漏れに気付けるようにしている。
 */
@Component
public class LabelResolver {

    private static final Logger log = LoggerFactory.getLogger(LabelResolver.class);

    private static final String EMPTY = "-";

    private final MessageSource messageSource;

    // 同じ値でリクエストのたびにログが出ないよう、一度報告したものを覚えておく(種類が有限なので上限は設けない)。
    private final Set<String> reportedMissing = ConcurrentHashMap.newKeySet();

    public LabelResolver(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** 英語名は公式APIの値をそのまま使うので、辞書は英語以外の言語にだけある。 */
    public String cardName(String rawName, Locale locale) {
        if (isEmpty(rawName)) {
            return EMPTY;
        }
        String key = "card." + rawName;
        reportIfUntranslated(key);
        return messageSource.getMessage(key, null, rawName, locale);
    }

    /**
     * ゲームモードは gameMode.name が "Ranked1v1_NewArena2" のようにアリーナやイベントごとに増えるため、
     * name だけの完全一致では追いつかない。name の辞書(gamemode.*)に無ければ、分類として安定している
     * 対戦種別 type の辞書(battletype.*)で表示する。どちらにも無ければ「その他」にし、内部IDは画面に出さない。
     */
    public String gameMode(String type, String name, Locale locale) {
        if (isEmpty(type) && isEmpty(name)) {
            return EMPTY;
        }
        String byName = isEmpty(name) ? null : messageSource.getMessage("gamemode." + name, null, null, locale);
        if (byName != null) {
            return byName;
        }
        // Challenge_AllCards_EventDeck_NoSet のようにイベントごとに増える名前は、完全一致では追いつかない。
        // type では trail(通常バトル)に混ざってしまうので、名前の接頭辞で先に分類する。
        String byPrefix = isEmpty(name) ? null : messageSource.getMessage(prefixKey(name), null, null, locale);
        if (byPrefix != null) {
            return byPrefix;
        }
        String byType = isEmpty(type) ? null : messageSource.getMessage("battletype." + type, null, null, locale);
        if (byType != null) {
            return byType;
        }
        if (reportedMissing.add("gamemode:" + type + "/" + name)) {
            log.warn("No label for game mode: type={}, name={}", type, name);
        }
        return messageSource.getMessage("battletype.other", null, EMPTY, locale);
    }

    /** レアリティ。辞書に無い新レアリティはAPIの生値をそのまま出す。 */
    public String rarity(String rawRarity, Locale locale) {
        if (isEmpty(rawRarity)) {
            return EMPTY;
        }
        return messageSource.getMessage("rarity." + rawRarity, null, rawRarity, locale);
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

    /** "Challenge_AllCards_EventDeck_NoSet" → "gamemodeprefix.Challenge"。区切りが無い名前は名前全体を接頭辞とみなす。 */
    private String prefixKey(String name) {
        int separator = name.indexOf('_');
        return "gamemodeprefix." + (separator < 0 ? name : name.substring(0, separator));
    }

    private void reportIfUntranslated(String key) {
        for (Locale supported : SupportedLanguages.SUPPORTED) {
            if (SupportedLanguages.INTERNATIONAL.getLanguage().equals(supported.getLanguage())) {
                continue;
            }
            if (messageSource.getMessage(key, null, null, supported) == null
                    && reportedMissing.add(supported.getLanguage() + ":" + key)) {
                log.warn("No {} translation for {}", supported.getLanguage(), key);
            }
        }
    }

    private boolean isEmpty(String value) {
        return value == null || value.isBlank();
    }
}
