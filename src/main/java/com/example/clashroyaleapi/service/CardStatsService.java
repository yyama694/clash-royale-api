package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.domain.CardStats;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** カードのステータス(cards/stats.tsv)。起動時に1回読むだけで、読み取り直したらデプロイで差し替える。 */
@Service
public class CardStatsService {

    private final CardStats stats;

    public CardStatsService() {
        this(load("cards/stats.tsv"));
    }

    CardStatsService(CardStats stats) {
        this.stats = stats;
    }

    public CardStats stats() {
        return stats;
    }

    private static CardStats load(String resource) {
        try (InputStream in = CardStatsService.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing resource: " + resource);
            }
            return CardStats.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
