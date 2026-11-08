package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.domain.CardAbility;
import com.example.clashroyaleapi.domain.CardStats;
import com.example.clashroyaleapi.domain.CardUnit;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * カードのステータス(cards/stats.tsv)と、カードとは別にヒットポイントを持つユニット(cards/units.tsv)、
 * ヒーローとチャンピオンのスキルのコスト(cards/abilities.tsv)。
 * 起動時に1回読むだけで、読み取り直したらデプロイで差し替える。
 */
@Service
public class CardStatsService {

    private final CardStats stats;
    private final List<CardUnit> units;
    private final List<CardAbility> abilities;

    public CardStatsService() {
        this(CardStats.parse(lines("cards/stats.tsv")), CardUnit.parse(lines("cards/units.tsv")),
                CardAbility.parse(lines("cards/abilities.tsv")));
    }

    CardStatsService(CardStats stats) {
        this(stats, List.of(), List.of());
    }

    CardStatsService(CardStats stats, List<CardUnit> units) {
        this(stats, units, List.of());
    }

    CardStatsService(CardStats stats, List<CardUnit> units, List<CardAbility> abilities) {
        this.stats = stats;
        this.units = units;
        this.abilities = abilities;
    }

    public CardStats stats() {
        return stats;
    }

    public List<CardUnit> units() {
        return units;
    }

    public List<CardAbility> abilities() {
        return abilities;
    }

    public Optional<CardAbility> ability(String card) {
        return abilities.stream().filter(ability -> ability.card().equals(card)).findFirst();
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
