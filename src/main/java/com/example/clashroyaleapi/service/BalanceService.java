package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.domain.BalanceBaseline;
import com.example.clashroyaleapi.domain.BalanceUpdate;
import com.example.clashroyaleapi.domain.CardUsage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    private final Map<LocalDate, BalanceBaseline> recordedBaselines;
    private final CardUsageService cardUsageService;
    private final CardService cardService;

    @Autowired
    public BalanceService(CardUsageService cardUsageService, CardService cardService) {
        this(BalanceUpdate.parse(lines("balance/changes.tsv")),
                BalanceBaseline.parse(lines("balance/usage-before.tsv")), cardUsageService, cardService);
    }

    BalanceService(List<BalanceUpdate> updates, Map<LocalDate, BalanceBaseline> recordedBaselines,
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

    /** そのカードに触れた調整(新しい順)。 */
    public List<BalanceUpdate> updatesOf(String card) {
        return updates.stream().filter(update -> update.touches(card)).toList();
    }

    /**
     * 調整の前の使用率。実施日(UTC)より前の最後の集計を使う。集計の履歴より前の調整は、手で残した数字があればそれを使う。
     * 実施の時刻は公式の記事に無いので、実施日の0時(UTC)で区切る。
     */
    public Optional<BalanceBaseline> baseline(BalanceUpdate update) {
        Optional<CardUsage> history = cardUsageService.lastBefore(update.date().atStartOfDay(ZoneOffset.UTC).toInstant());
        if (history.isPresent()) {
            Map<String, Integer> ids = cardService.cardsByName().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().id()));
            return Optional.of(BalanceBaseline.of(history.get(), ids));
        }
        return Optional.ofNullable(recordedBaselines.get(update.date()));
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
