package com.example.clashroyaleapi.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 2つの記録の間のランキングの動き。上位の顔ぶれと順位の上下、レーティングを大きく上げた人、上位から外れた人。
 *
 * @param before   比べる相手。シーズンの最初の記録のように相手が無ければ null で、変化は出さない
 * @param top      after の上位(前の順位とレーティングの増減付き)
 * @param climbers レーティングを上げた人を、上げた幅の大きい順に。before の時点でランキング(上位1000人)にいなかった人は
 *                 上げた幅が分からないので入らない
 * @param dropouts before の上位にいて、after の上位にいない人(before の順位の順)
 */
public record RankingMovements(RankingSnapshot before, RankingSnapshot after, List<TopEntry> top,
        List<Climber> climbers, List<Dropout> dropouts) {

    /** @param previousRank before に載っていなければ null(before が null のときも null) */
    public record TopEntry(RankingSnapshot.Entry entry, Integer previousRank, Integer ratingChange) {
    }

    public record Climber(RankingSnapshot.Entry entry, int previousRank, int ratingGain) {
    }

    /** @param current after のランキング(上位1000人)にもいなければ null */
    public record Dropout(RankingSnapshot.Entry previous, RankingSnapshot.Entry current) {
    }

    public static RankingMovements between(RankingSnapshot before, RankingSnapshot after, int topSize,
            int climberLimit) {
        if (before == null) {
            List<TopEntry> top = after.entries().stream()
                    .limit(topSize)
                    .map(entry -> new TopEntry(entry, null, null))
                    .toList();
            return new RankingMovements(null, after, top, List.of(), List.of());
        }
        Map<String, RankingSnapshot.Entry> previous = before.byTag();
        Map<String, RankingSnapshot.Entry> current = after.byTag();

        List<TopEntry> top = after.entries().stream()
                .limit(topSize)
                .map(entry -> {
                    RankingSnapshot.Entry was = previous.get(entry.tag());
                    return was == null ? new TopEntry(entry, null, null)
                            : new TopEntry(entry, was.rank(), entry.rating() - was.rating());
                })
                .toList();

        List<Climber> climbers = after.entries().stream()
                .filter(entry -> previous.containsKey(entry.tag()))
                .map(entry -> {
                    RankingSnapshot.Entry was = previous.get(entry.tag());
                    return new Climber(entry, was.rank(), entry.rating() - was.rating());
                })
                .filter(climber -> climber.ratingGain() > 0)
                .sorted(Comparator.comparingInt(Climber::ratingGain).reversed()
                        .thenComparingInt(climber -> climber.entry().rank()))
                .limit(climberLimit)
                .toList();

        Set<String> topTags = top.stream().map(entry -> entry.entry().tag()).collect(Collectors.toSet());
        List<Dropout> dropouts = before.entries().stream()
                .limit(topSize)
                .filter(entry -> !topTags.contains(entry.tag()))
                .map(entry -> new Dropout(entry, current.get(entry.tag())))
                .toList();

        return new RankingMovements(before, after, top, climbers, dropouts);
    }
}
