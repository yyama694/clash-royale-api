package com.example.clashroyaleapi.web;

import com.ibm.icu.lang.UScript;
import com.ibm.icu.text.DisplayContext;
import com.ibm.icu.text.LocaleDisplayNames;
import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.text.MessagePattern;
import com.ibm.icu.text.PluralRules;
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
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * messages*.properties 同士の整合性を確認する。キーを片方の言語にだけ足す漏れは、画面を見ないと気付けないため。
 */
class MessagesConsistencyTest {

    private static final String PRIVACY_PREFIX = "privacy.";

    /**
     * 英語(基本ファイル)には無くてよいキー。カード名は公式APIの英語名をそのまま使い、
     * 国名・カード名の読み仮名は漢字の読みが要る言語に、カードの通称は通称が定着している言語にだけある。
     * プライバシーポリシーの本文は英語と日本語にだけあり、他の言語では英語版が出る(下のテストで英語と日本語をそろえる)。
     */
    private static final List<String> LANGUAGE_SPECIFIC_PREFIXES =
            List.of("cardalias.", "cardreading.", "country.reading.", PRIVACY_PREFIX);

    private static Properties load(String name) throws IOException {
        try (InputStream in = MessagesConsistencyTest.class.getResourceAsStream("/" + name)) {
            Properties properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return properties;
        }
    }

    // card.pageTitle のような画面用のキーは全言語に要るので、カード名(英語名がキー)だけを除く。
    private static Set<String> sharedKeys(Properties properties) {
        return properties.stringPropertyNames().stream()
                .filter(key -> !isCardName(key))
                .filter(key -> LANGUAGE_SPECIFIC_PREFIXES.stream().noneMatch(key::startsWith))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** 基本ファイル(英語)と、英語以外の対応言語のファイル。言語を足したらここにも自動で入る。 */
    private static List<String> bundleFiles() {
        return SupportedLanguages.SUPPORTED.stream().map(MessagesConsistencyTest::bundleFile).toList();
    }

    private static String bundleFile(Locale locale) {
        return locale.equals(SupportedLanguages.INTERNATIONAL)
                ? "messages.properties"
                : "messages_" + locale.getLanguage() + ".properties";
    }

    @Test
    void プライバシーポリシーの本文は英語と日本語だけにそろって持つ() throws IOException {
        Set<String> english = privacyKeys(load("messages.properties"));

        assertFalse(english.isEmpty());
        for (String file : bundleFiles()) {
            Set<String> keys = privacyKeys(load(file));
            assertEquals(file.equals("messages_ja.properties") || file.equals("messages.properties") ? english : Set.of(),
                    keys, file);
        }
    }

    private static Set<String> privacyKeys(Properties properties) {
        return properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith(PRIVACY_PREFIX))
                .collect(Collectors.toCollection(TreeSet::new));
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

    private static Set<String> cardKeys(Properties properties) {
        return properties.stringPropertyNames().stream()
                .filter(MessagesConsistencyTest::isCardName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    // card.pageTitle のような画面用のキーと区別するため、英語名(大文字か数字で始まる)のものだけをカード名とみなす。
    private static boolean isCardName(String key) {
        return key.matches("card\\.[A-Z0-9].*");
    }

    /**
     * 数(#)を出す複数形は、その言語で必要な形をすべて書く。書き漏れた形はotherで出るので、ロシア語で
     * few を書き忘れると「2 побед」のような誤りになる(英語・日本語の画面を見ても気付けない)。
     * 必要な形は0〜1000の整数で実際に使われるものとする。フランス語・スペイン語などの many は100万単位にしか
     * 使われないので求めない。数を出さない複数形(ロシア語の動詞 сыграл/сыграли など)は、other で足りることがあるので対象外。
     */
    @Test
    void 数を出す複数形はその言語で必要な形をすべて持つ() throws IOException {
        Set<String> problems = new TreeSet<>();
        for (Locale locale : SupportedLanguages.SUPPORTED) {
            Set<String> required = requiredPluralForms(locale);
            String file = bundleFile(locale);
            Properties properties = load(file);
            for (String key : properties.stringPropertyNames()) {
                Set<String> missing = missingPluralForms(properties.getProperty(key), required);
                if (!missing.isEmpty()) {
                    problems.add(file + " の " + key + " に " + missing + " が無い");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void 複数形の書き漏れの判定() {
        Locale russian = Locale.forLanguageTag("ru");
        assertEquals(Set.of("few", "many", "one"), requiredPluralForms(russian));
        assertEquals(Set.of("one", "other"), requiredPluralForms(Locale.forLanguageTag("fr")));
        assertEquals(Set.of("other"), requiredPluralForms(Locale.JAPANESE));

        assertEquals(Set.of("few"), missingPluralForms(
                "{0, plural, one{# победа} many{# побед} other{# победы}}", requiredPluralForms(russian)));
        assertEquals(Set.of(), missingPluralForms(
                "{0, plural, one{# победа} few{# победы} many{# побед} other{# победы}}", requiredPluralForms(russian)));
        // 数を出さない複数形(動詞の語形だけを変える)は対象外。
        assertEquals(Set.of(), missingPluralForms("{0, plural, one{сыграл} other{сыграли}}", requiredPluralForms(russian)));
    }

    private static Set<String> requiredPluralForms(Locale locale) {
        PluralRules rules = PluralRules.forLocale(ULocale.forLocale(locale));
        return IntStream.rangeClosed(0, 1000)
                .mapToObj(n -> rules.select(n))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> missingPluralForms(String message, Set<String> required) {
        MessagePattern pattern = new MessagePattern(message);
        Set<String> missing = new TreeSet<>();
        for (int start = 0; start < pattern.countParts(); start++) {
            MessagePattern.Part part = pattern.getPart(start);
            if (part.getType() != MessagePattern.Part.Type.ARG_START
                    || part.getArgType() != MessagePattern.ArgType.PLURAL) {
                continue;
            }
            Set<String> selectors = new TreeSet<>();
            boolean showsNumber = false;
            for (int i = start + 1; i < pattern.getLimitPartIndex(start); i++) {
                MessagePattern.Part inner = pattern.getPart(i);
                if (inner.getType() == MessagePattern.Part.Type.ARG_SELECTOR) {
                    selectors.add(pattern.getSubstring(inner));
                } else if (inner.getType() == MessagePattern.Part.Type.REPLACE_NUMBER) {
                    showsNumber = true;
                }
            }
            if (showsNumber) {
                required.stream().filter(form -> !selectors.contains(form)).forEach(missing::add);
            }
        }
        return missing;
    }

    @Test
    void 漢字で始まるカード名にはすべて読み仮名がある() throws IOException {
        Properties japanese = load("messages_ja.properties");
        Set<String> missing = new TreeSet<>();
        for (String key : cardKeys(japanese)) {
            String name = japanese.getProperty(key);
            String rawName = key.substring("card.".length());
            if (UScript.getScript(name.codePointAt(0)) == UScript.HAN
                    && !japanese.containsKey("cardreading." + rawName)) {
                missing.add(rawName + "=" + name);
            }
        }
        assertTrue(missing.isEmpty(), "読み仮名(cardreading.*)が無いカード: " + missing);
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
