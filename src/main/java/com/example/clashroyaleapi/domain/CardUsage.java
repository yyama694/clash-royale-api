package com.example.clashroyaleapi.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link TopDecks} から、カードごとの使用率・順位・一緒に使われるカードを求める。
 * 通常のカードとタワーユニットはデッキの別枠なので、順位も別々に数える。
 */
public final class CardUsage {

    /** 一緒に使われるカードの表示件数。 */
    static final int PARTNER_LIMIT = 5;
    // 使用者が少ないと「1人中1人=100%」のような偏った値になるため、一緒に使われるカードはこの人数から出す。
    static final int MIN_USERS_FOR_PARTNERS = 10;

    private final Instant collectedAt;
    private final int sampleSize;
    private final Map<Integer, Usage> byCard;

    private CardUsage(Instant collectedAt, int sampleSize, Map<Integer, Usage> byCard) {
        this.collectedAt = collectedAt;
        this.sampleSize = sampleSize;
        this.byCard = byCard;
    }

    /**
     * @param rank     使用者の多い順の順位(同数は同順位)。rankedOf は順位を付けたカードの数(1人以上が使っていたもの)
     * @param partners このカードを使っていた人のうち、同じデッキに入れていた割合の高いカード
     */
    public record Usage(int users, int sampleSize, int rank, int rankedOf, List<Partner> partners) {

        public double percent() {
            return sampleSize == 0 ? 0 : users * 100.0 / sampleSize;
        }
    }

    public record Partner(int cardId, int users, int percent) {
    }

    public static CardUsage of(TopDecks topDecks) {
        Map<Integer, Integer> cardUsers = new HashMap<>();
        Map<Integer, Integer> towerUsers = new HashMap<>();
        Map<Integer, Map<Integer, Integer>> pairs = new HashMap<>();
        for (TopDecks.SampledDeck deck : topDecks.decks()) {
            // 同じカードが2回入ることは無いはずだが、APIの値をそのまま数えて100%を超えないよう重複を除く。
            List<Integer> cards = deck.cardIds().stream().distinct().toList();
            for (int card : cards) {
                cardUsers.merge(card, 1, Integer::sum);
                Map<Integer, Integer> partners = pairs.computeIfAbsent(card, k -> new HashMap<>());
                for (int other : cards) {
                    if (other != card) {
                        partners.merge(other, 1, Integer::sum);
                    }
                }
            }
            if (deck.towerTroopId() != null) {
                towerUsers.merge(deck.towerTroopId(), 1, Integer::sum);
            }
        }
        int sampleSize = topDecks.decks().size();
        Map<Integer, Usage> byCard = new HashMap<>();
        addRanked(byCard, cardUsers, sampleSize, pairs);
        addRanked(byCard, towerUsers, sampleSize, Map.of());
        return new CardUsage(topDecks.collectedAt(), sampleSize, byCard);
    }

    private static void addRanked(Map<Integer, Usage> byCard, Map<Integer, Integer> users, int sampleSize,
            Map<Integer, Map<Integer, Integer>> pairs) {
        users.forEach((card, count) -> {
            int rank = 1 + (int) users.values().stream().filter(other -> other > count).count();
            byCard.put(card, new Usage(count, sampleSize, rank, users.size(),
                    partnersOf(count, pairs.getOrDefault(card, Map.of()))));
        });
    }

    private static List<Partner> partnersOf(int users, Map<Integer, Integer> partners) {
        if (users < MIN_USERS_FOR_PARTNERS) {
            return List.of();
        }
        return partners.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(PARTNER_LIMIT)
                .map(e -> new Partner(e.getKey(), e.getValue(), Math.round(e.getValue() * 100f / users)))
                .toList();
    }

    public Instant collectedAt() {
        return collectedAt;
    }

    public int sampleSize() {
        return sampleSize;
    }

    /** 標本の誰も使っていなかったカードは、使用者0人・順位なし(rank 0)として返す。 */
    public Usage usageOf(int cardId) {
        return Optional.ofNullable(byCard.get(cardId))
                .orElseGet(() -> new Usage(0, sampleSize, 0, 0, List.of()));
    }
}
