package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.domain.CardStats;
import com.example.clashroyaleapi.domain.CardUnit;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * カードのステータス(cards/stats.tsv)と、カードとは別にヒットポイントを持つユニット(cards/units.tsv)。
 * 起動時に1回読むだけで、読み取り直したらデプロイで差し替える。
 */
@Service
public class CardStatsService {

    private final CardStats stats;
    private final List<CardUnit> units;

    public CardStatsService() {
        this(CardStats.parse(lines("cards/stats.tsv")), CardUnit.parse(lines("cards/units.tsv")));
    }

    CardStatsService(CardStats stats) {
        this(stats, List.of());
    }

    CardStatsService(CardStats stats, List<CardUnit> units) {
        this.stats = stats;
        this.units = units;
    }

    public CardStats stats() {
        return stats;
    }

    public List<CardUnit> units() {
        return units;
    }

    private static List<String> lines(String resource) {
        try (InputStream in = CardStatsService.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing resource: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
