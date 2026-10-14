package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.Tags;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 使用中のデッキと同じカードを使っている世界トップ層の人数(プレイヤー情報画面の「同じデッキの世界ランカー」)。
 * 8枚とも同じ人がいなければ7枚、6枚と減らし、その枚数の組み合わせのうち一番多くの人が使っているものを選ぶ。
 * 形(進化・ヒーロー)とタワーユニットは区別しない(トッププレイヤーのデッキ画面の絞り込みと同じ)。
 *
 * @param cardIds      共通のカード。元のデッキの並び順
 * @param players      使っている人数。本人は数えない
 * @param sampleSize   集計した人数(本人を含む)
 * @param selfExcluded 本人も集計に入っていたか(デッキ画面へのリンク先では本人も数えられ、人数が1人多くなる)
 */
public record SimilarDecks(List<Integer> cardIds, int players, int sampleSize, boolean selfExcluded) {

    /** これより少ない枚数しか同じでなければ「同じデッキ」とは言えないので出さない。 */
    public static final int MIN_SHARED = 6;

    public int sharedCards() {
        return cardIds.size();
    }

    /**
     * @param deck    使用中のデッキのカード(タワーユニットを除く)。8枚そろっていなければ空を返す
     * @param sample  トッププレイヤーのデッキ。誰のデッキか分かるものだけ
     * @param ownTag  見ているプレイヤーのタグ
     */
    public static Optional<SimilarDecks> find(List<Integer> deck, List<TopDecks.SampledDeck> sample, String ownTag) {
        if (deck.size() != Deck.SIZE || Set.copyOf(deck).size() != Deck.SIZE) {
            return Optional.empty();
        }
        String own = Tags.normalize(ownTag);
        boolean selfIncluded = sample.stream().anyMatch(d -> isOwn(d, own));
        List<Set<Integer>> others = sample.stream()
                .filter(d -> !isOwn(d, own))
                .map(d -> Set.copyOf(d.cardIds()))
                .toList();
        for (int size = Deck.SIZE; size >= MIN_SHARED; size--) {
            List<Integer> best = null;
            int bestCount = 0;
            for (List<Integer> subset : subsets(deck, size)) {
                int count = (int) others.stream().filter(cards -> cards.containsAll(subset)).count();
                if (count > bestCount) {
                    best = subset;
                    bestCount = count;
                }
            }
            if (best != null) {
                return Optional.of(new SimilarDecks(best, bestCount, sample.size(), selfIncluded));
            }
        }
        return Optional.empty();
    }

    private static boolean isOwn(TopDecks.SampledDeck deck, String ownTag) {
        return deck.player() != null && Tags.normalize(deck.player().tag()).equals(ownTag);
    }

    /** 並び順を保った、size 枚の組み合わせすべて。 */
    static List<List<Integer>> subsets(List<Integer> cards, int size) {
        List<List<Integer>> result = new ArrayList<>();
        collect(cards, size, 0, new ArrayList<>(), result);
        return result;
    }

    private static void collect(List<Integer> cards, int size, int from, List<Integer> current,
            List<List<Integer>> result) {
        if (current.size() == size) {
            result.add(List.copyOf(current));
            return;
        }
        for (int i = from; i <= cards.size() - (size - current.size()); i++) {
            current.add(cards.get(i));
            collect(cards, size, i + 1, current, result);
            current.removeLast();
        }
    }
}
