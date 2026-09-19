package com.example.clashroyaleapi.web;

import com.ibm.icu.lang.UScript;
import com.ibm.icu.text.DisplayContext;
import com.ibm.icu.text.LocaleDisplayNames;
import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.util.ULocale;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * messages*.properties 同士の整合性を確認する。キーを片方の言語にだけ足す漏れは、画面を見ないと気付けないため。
 */
class MessagesConsistencyTest {

    /**
     * 英語(基本ファイル)には無くてよいキー。カード名は公式APIの英語名をそのまま使い、
     * 国名の読み仮名は漢字の読みが要る言語にだけある。
     */
    private static final List<String> LANGUAGE_SPECIFIC_PREFIXES = List.of("card.", "country.reading.");

    private static Properties load(String name) throws IOException {
        try (InputStream in = MessagesConsistencyTest.class.getResourceAsStream("/" + name)) {
            Properties properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return properties;
        }
    }

    private static Set<String> sharedKeys(Properties properties) {
        return properties.stringPropertyNames().stream()
                .filter(key -> LANGUAGE_SPECIFIC_PREFIXES.stream().noneMatch(key::startsWith))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** 基本ファイル(英語)と、英語以外の対応言語のファイル。言語を足したらここにも自動で入る。 */
    private static List<String> bundleFiles() {
        return SupportedLanguages.SUPPORTED.stream()
                .map(locale -> locale.equals(SupportedLanguages.INTERNATIONAL)
                        ? "messages.properties"
                        : "messages_" + locale.getLanguage() + ".properties")
                .toList();
    }

    @Test
    void 対応言語すべてで英語とキーが一致している() throws IOException {
        Set<String> english = sharedKeys(load("messages.properties"));
        for (String file : bundleFiles()) {
            Set<String> translated = sharedKeys(load(file));

            Set<String> missingInTranslation = new TreeSet<>(english);
            missingInTranslation.removeAll(translated);
            Set<String> missingInEnglish = new TreeSet<>(translated);
            missingInEnglish.removeAll(english);

            assertEquals(Set.of(), missingInTranslation, file + " に無いキー");
            assertEquals(Set.of(), missingInEnglish, "messages.properties に無いキー(" + file + ")");
        }
    }

    @Test
    void 英語以外の対応言語はすべてのカード名を持つ() throws IOException {
        Set<String> japaneseCards = cardKeys(load("messages_ja.properties"));
        for (String file : bundleFiles()) {
            if (file.equals("messages.properties")) {
                continue;
            }
            assertEquals(japaneseCards, cardKeys(load(file)), file + " のカード名が日本語と揃っていない");
        }
    }

    @Test
    void すべてのメッセージがICUの書式として解釈できる() throws IOException {
        for (String file : bundleFiles()) {
            Properties properties = load(file);
            for (String key : properties.stringPropertyNames()) {
                try {
                    new MessageFormat(properties.getProperty(key), ULocale.ENGLISH);
                } catch (IllegalArgumentException e) {
                    fail(file + " の " + key + " がICUの書式として不正: " + e.getMessage());
                }
            }
        }
    }

    // card.pageTitle のような画面用のキーと区別するため、英語名(大文字か数字で始まる)のものだけを数える。
    private static Set<String> cardKeys(Properties properties) {
        return properties.stringPropertyNames().stream()
                .filter(key -> key.matches("card\\.[A-Z0-9].*"))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void 漢字で始まる国名にはすべて読み仮名がある() throws IOException {
        Properties japanese = load("messages_ja.properties");
        LocaleDisplayNames names = LocaleDisplayNames.getInstance(ULocale.JAPANESE, DisplayContext.LENGTH_SHORT);

        Set<String> missing = new TreeSet<>();
        for (String code : Locale.getISOCountries()) {
            String name = names.regionDisplayName(code);
            if (UScript.getScript(name.codePointAt(0)) == UScript.HAN
                    && !japanese.containsKey("country.reading." + code)) {
                missing.add(code + "=" + name);
            }
        }
        assertTrue(missing.isEmpty(), "読み仮名(country.reading.*)が無い国: " + missing);
    }
}
