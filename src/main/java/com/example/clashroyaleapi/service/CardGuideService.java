package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.domain.CardGuide;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * カード詳細画面の解説(cards/guides/&lt;言語&gt;/&lt;カードID&gt;.txt)。
 * 解説は手で書くので一部のカード・言語にしかなく、無い言語ではほかの言語の解説を代わりに出さない。
 * 起動時に1回読むだけで、書き足したらデプロイで差し替える。
 */
@Service
public class CardGuideService {

    private static final Pattern PATH = Pattern.compile("cards/guides/([a-z]{2})/(\\d+)\\.txt$");

    private final Map<String, CardGuide> guides;

    public CardGuideService() {
        this(load());
    }

    CardGuideService(Map<String, CardGuide> guides) {
        this.guides = Map.copyOf(guides);
    }

    public Optional<CardGuide> guide(int cardId, Locale locale) {
        return Optional.ofNullable(guides.get(key(locale.getLanguage(), cardId)));
    }

    private static String key(String language, int cardId) {
        return language + "/" + cardId;
    }

    private static Map<String, CardGuide> load() {
        Map<String, CardGuide> guides = new HashMap<>();
        try {
            for (Resource resource : new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:cards/guides/*/*.txt")) {
                Matcher m = PATH.matcher(resource.getURL().toString());
                if (!m.find()) {
                    throw new IllegalStateException("unexpected guide file: " + resource);
                }
                String text = resource.getContentAsString(StandardCharsets.UTF_8);
                try {
                    guides.put(key(m.group(1), Integer.parseInt(m.group(2))), CardGuide.parse(text.lines().toList()));
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException("bad guide file: " + resource + ": " + e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return guides;
    }
}
