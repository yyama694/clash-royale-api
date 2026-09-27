package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.domain.TopDecks;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * トッププレイヤーのデッキ画面。ランク戦の世界上位のプレイヤーが直近のランク戦で使ったデッキを、順位の順に並べる。
 * データは{@link CardUsageService}が持つ1日1回の集計をそのまま使い、公式APIは呼ばない。
 */
@Service
public class TopPlayerDeckService {

    public static final int PAGE_SIZE = 100;

    private final CardUsageService usageService;

    public TopPlayerDeckService(CardUsageService usageService) {
        this.usageService = usageService;
    }

    /**
     * @param total   絞り込み後の人数
     * @param from    このページの先頭が何人目か(1始まり)。該当者がいなければ0
     * @param hasNext 次のページがあるか
     */
    public record Page(Instant collectedAt, List<TopDecks.SampledDeck> decks, int total, int page, int from,
                       boolean hasNext) {
    }

    /**
     * まだ一度も集計していない、または誰のデッキかを持たない古い形式の集計しか無いときは空。
     *
     * @param cardId 指定されたら、そのカード(タワーユニットを含む)を使っている人だけにする
     * @param page   1始まり。範囲外は最後のページとして扱う
     */
    public Optional<Page> page(Integer cardId, int page) {
        return usageService.topDecks().flatMap(topDecks -> {
            List<TopDecks.SampledDeck> ranked = topDecks.decks().stream()
                    .filter(deck -> deck.player() != null)
                    .sorted(Comparator.comparingInt(deck -> deck.player().rank()))
                    .toList();
            if (ranked.isEmpty()) {
                return Optional.empty();
            }
            List<TopDecks.SampledDeck> matched = cardId == null ? ranked
                    : ranked.stream().filter(deck -> deck.contains(cardId)).toList();
            int lastPage = Math.max(1, (matched.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            int current = Math.min(Math.max(page, 1), lastPage);
            int start = (current - 1) * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, matched.size());
            return Optional.of(new Page(topDecks.collectedAt(), matched.subList(start, end), matched.size(), current,
                    matched.isEmpty() ? 0 : start + 1, current < lastPage));
        });
    }
}
