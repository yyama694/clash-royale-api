package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.domain.BalanceSnapshot;
import com.example.clashroyaleapi.domain.BalanceUpdate;
import com.example.clashroyaleapi.domain.BalanceUsage;
import com.example.clashroyaleapi.domain.CardUsage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * バランス調整の内容(公式のリリースノートを {@code balance/changes.tsv} に写したもの)と、調整の前後の使用率。
 */
@Service
public class BalanceService {

    private final List<BalanceUpdate> updates;
    private final Map<LocalDate, BalanceSnapshot> recordedBaselines;
    private final CardUsageService cardUsageService;
    private final CardService cardService;

    @Autowired
    public BalanceService(CardUsageService cardUsageService, CardService cardService) {
        this(BalanceUpdate.parse(lines("balance/changes.tsv")),
                BalanceSnapshot.parse(lines("balance/usage-before.tsv")), cardUsageService, cardService);
    }

    BalanceService(List<BalanceUpdate> updates, Map<LocalDate, BalanceSnapshot> recordedBaselines,
            CardUsageService cardUsageService, CardService cardService) {
        this.updates = updates;
        this.recordedBaselines = recordedBaselines;
        this.cardUsageService = cardUsageService;
        this.cardService = cardService;
    }

    /** 新しい調整から順に。 */
    public List<BalanceUpdate> updates() {
        return updates;
    }

    /** その日に実施した調整。 */
    public Optional<BalanceUpdate> update(LocalDate date) {
        return updates.stream().filter(update -> update.date().equals(date)).findFirst();
    }

    /** そのカードに触れた調整(新しい順)。 */
    public List<BalanceUpdate> updatesOf(String card) {
        return updates.stream().filter(update -> update.touches(card)).toList();
    }

    /**
     * 調整の対象カードの使用率の推移(調整前・1日後・3日後・7日後・今)。そのカードが後の調整でまた変わっていれば、
     * 「今」の代わりに次の調整の前の最後の集計で止める。
     */
    public BalanceUsage usage(BalanceUpdate update) {
        Map<String, Integer> ids = cardService.cardsByName().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().id()));
        Map<Integer, BalanceSnapshot> daysAfter = new HashMap<>();
        for (int days : BalanceUsage.DAYS_AFTER) {
            cardUsageService.firstOn(update.date().plusDays(days))
                    .ifPresent(usage -> daysAfter.put(days, BalanceSnapshot.of(usage, ids)));
        }
        Map<String, BalanceUsage.Next> nexts = new HashMap<>();
        for (BalanceUpdate.CardChanges changes : update.byCard()) {
            updates.stream()
                    .filter(other -> other.date().isAfter(update.date()) && other.touches(changes.card()))
                    .min(Comparator.comparing(BalanceUpdate::date))
                    .ifPresent(next -> {
                        Instant start = startOf(next.date());
                        nexts.put(changes.card(), new BalanceUsage.Next(start,
                                cardUsageService.lastBefore(start).map(usage -> BalanceSnapshot.of(usage, ids))
                                        .orElse(null)));
                    });
        }
        return BalanceUsage.of(update, baseline(update, ids).orElse(null), daysAfter,
                cardUsageService.current().map(usage -> BalanceSnapshot.of(usage, ids)).orElse(null), nexts);
    }

    /**
     * 調整の前の使用率。実施日(UTC)より前の最後の集計を使う。集計の履歴より前の調整は、手で残した数字があればそれを使う。
     * 実施の時刻は公式の記事に無いので、実施日の0時(UTC)で区切る。
     */
    private Optional<BalanceSnapshot> baseline(BalanceUpdate update, Map<String, Integer> ids) {
        Optional<CardUsage> history = cardUsageService.lastBefore(startOf(update.date()));
        if (history.isPresent()) {
            return Optional.of(BalanceSnapshot.of(history.get(), ids));
        }
        return Optional.ofNullable(recordedBaselines.get(update.date()));
    }

    private static Instant startOf(LocalDate day) {
        return day.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static List<String> lines(String resource) {
        try (InputStream in = BalanceService.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing resource: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
