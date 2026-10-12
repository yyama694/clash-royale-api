package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.domain.TopDecks;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * トッププレイヤーのデッキ画面。ランク戦の世界上位のプレイヤーが直近のランク戦で使ったデッキを、順位の順に並べる。
 * データは{@link CardUsageService}が持つ1日1回の集計をそのまま使い、公式APIは呼ばない。
 */
@Service
public class TopPlayerDeckService {

    public static final int PAGE_SIZE = 100;
    /** 絞り込みで同時に選べるカードの数(2026-10-10にユーザーが決定)。タワーユニットもこの数に含める。 */
    public static final int MAX_FILTER_CARDS = 8;

    private final CardUsageService usageService;

    public TopPlayerDeckService(CardUsageService usageService) {
        this.usageService = usageService;
    }

    /**
     * @param total      絞り込み後の人数
     * @param from       このページの先頭が何人目か(1始まり)。該当者がいなければ0
     * @param hasNext    次のページがあるか
     * @param sampleSize 絞り込む前の人数(集計した全員)
     * @param topRank    絞り込み後の最上位の順位。該当者がいなければ null
     * @param finishedSeason 前のシーズンの最終順位の上位で集めたときのシーズン。今シーズンのランキングで集めたときは null
     */
    public record Page(Instant collectedAt, List<TopDecks.SampledDeck> decks, int total, int page, int from,
                       boolean hasNext, int sampleSize, Integer topRank, YearMonth finishedSeason) {
    }

    /**
     * 絞り込みに使うカード。指定の順を保ち、重複と一覧に無いカードを除いて先頭の{@link #MAX_FILTER_CARDS}枚にする。
     * 一覧に無いカードで0件の画面を出さないため。
     *
     * @param requested URLで指定された順。空の選択欄から来た null を含むことがある
     */
    public static List<Integer> filterCards(List<Integer> requested, Set<Integer> knownCardIds) {
        return requested.stream()
                .filter(id -> id != null && knownCardIds.contains(id))
                .distinct()
                .limit(MAX_FILTER_CARDS)
                .toList();
    }

    public Optional<Page> page(List<Integer> cardIds, int page) {
        return page(cardIds, page, PAGE_SIZE);
    }

    /**
     * まだ一度も集計していない、または誰のデッキかを持たない古い形式の集計しか無いときは空。
     * カード詳細画面は、このカードを使っている人の上位だけを出すため、1ページの人数を小さくして先頭のページを使う。
     *
     * @param cardIds 指定されたら、そのカード(タワーユニットを含む)をすべて使っている人だけにする。空なら全員
     * @param page    1始まり。範囲外は最後のページとして扱う
     */
    public Optional<Page> page(List<Integer> cardIds, int page, int pageSize) {
        return usageService.topDecks().flatMap(topDecks -> {
            List<TopDecks.SampledDeck> ranked = topDecks.decks().stream()
                    .filter(deck -> deck.player() != null)
                    .sorted(Comparator.comparingInt(deck -> deck.player().rank()))
                    .toList();
            if (ranked.isEmpty()) {
                return Optional.empty();
            }
            List<TopDecks.SampledDeck> matched = ranked.stream()
                    .filter(deck -> cardIds.stream().allMatch(deck::contains))
                    .toList();
            int lastPage = Math.max(1, (matched.size() + pageSize - 1) / pageSize);
            int current = Math.min(Math.max(page, 1), lastPage);
            int start = (current - 1) * pageSize;
            int end = Math.min(start + pageSize, matched.size());
            return Optional.of(new Page(topDecks.collectedAt(), matched.subList(start, end), matched.size(), current,
                    matched.isEmpty() ? 0 : start + 1, current < lastPage, ranked.size(),
                    matched.isEmpty() ? null : matched.get(0).player().rank(), topDecks.finishedSeason()));
        });
    }
}
