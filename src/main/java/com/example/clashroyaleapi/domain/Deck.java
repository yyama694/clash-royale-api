package com.example.clashroyaleapi.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/**
 * デッキ8枚から求める指標と、ゲームにデッキをコピーするリンク。
 * 8枚そろっていないデッキ(特殊モード)やエリクサーが分からないカードを含むデッキは、指標を出さない。
 * 一部だけで計算した値は、ゲーム内やほかのサイトの数字と食い違って紛らわしいため。
 */
public final class Deck {

    public static final int SIZE = 8;
    private static final int CYCLE_CARDS = 4;

    private Deck() {
    }

    /** elixirCosts は公式APIのelixirCost。値が無いカードは null で渡す。 */
    public static OptionalDouble averageElixir(List<Integer> elixirCosts) {
        if (!isComplete(elixirCosts)) {
            return OptionalDouble.empty();
        }
        return elixirCosts.stream().mapToInt(Integer::intValue).average();
    }

    /**
     * 8枚のカードレベルの平均。levels はゲーム内表記に直した値(CardLevel.inGame)で渡すこと。
     * APIの生値はレアリティごとに数え直しているため、そのまま平均すると意味のない数字になる。
     * タワーユニットはレベルの体系がカードと別なので含めない。
     */
    public static OptionalDouble averageLevel(List<Integer> levels) {
        if (!isComplete(levels)) {
            return OptionalDouble.empty();
        }
        return levels.stream().mapToInt(Integer::intValue).average();
    }

    /** 4枚サイクル: 最も軽い4枚のエリクサーの合計。デッキが一巡して同じカードに戻るまでの速さの目安。 */
    public static OptionalInt fourCardCycle(List<Integer> elixirCosts) {
        if (!isComplete(elixirCosts)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(elixirCosts.stream().mapToInt(Integer::intValue).sorted().limit(CYCLE_CARDS).sum());
    }

    /**
     * ゲームを開いてデッキをコピーするリンク。スマホでクラロワが入っていればゲームが開く。
     * 形式は公式の文書が無いため、RoyaleAPIが実際に使っているリンクに合わせた(2026-09-19に確認)。
     * l=Royals も同じく合わせたもので、意味は公開されていない。
     * カードの並び順がそのままゲーム内のスロット順(先頭が進化・ヒーロー枠)になるので、対戦履歴の順を保つこと。
     */
    public static Optional<String> copyUrl(List<Integer> cardIds, Integer towerTroopId) {
        if (cardIds == null || cardIds.size() != SIZE) {
            return Optional.empty();
        }
        String deck = cardIds.stream().map(String::valueOf).collect(Collectors.joining(";"));
        String tower = towerTroopId == null ? "" : "&tt=" + towerTroopId;
        return Optional.of("https://link.clashroyale.com/en?clashroyale://copyDeck?deck=" + deck + tower + "&l=Royals");
    }

    private static boolean isComplete(List<Integer> values) {
        return values != null && values.size() == SIZE && values.stream().allMatch(Objects::nonNull);
    }
}
