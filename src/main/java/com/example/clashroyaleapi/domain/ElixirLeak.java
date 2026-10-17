package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 消費漏れ(エリクサーが満タンのまま使えずに無駄になった量。公式APIの elixirLeaked で、ゲーム内の観戦・リプレイ画面の
 * 「消費漏れ」)の、勝った対戦・負けた対戦それぞれの中央値。
 *
 * 平均ではなく中央値にするのは、放置したとみられる対戦(1戦で40〜80)に引きずられないため。
 * 特殊ルールのイベントはエリクサーの増え方が違って比べられない(7倍エリクサーでは1戦の中央値が131。2026-10-18に約6,000戦で確認)ので、
 * プレイヤーの対戦履歴からは、標準のルールで戦う1対1(ランク戦とトロフィー目標)だけを数える。
 *
 * @param battles    数えた対戦の数(引き分けは除く)
 * @param winMedian  勝った対戦の中央値。勝った対戦が {@link #MIN_BATTLES_PER_RESULT} 戦に満たなければ null
 * @param loseMedian 負けた対戦の中央値。負けた対戦が {@link #MIN_BATTLES_PER_RESULT} 戦に満たなければ null
 */
public record ElixirLeak(int battles, Double winMedian, Double loseMedian) {

    /** 中央値を出す最低の対戦数。1〜2戦では、その対戦の展開でほぼ決まってしまうため。 */
    public static final int MIN_BATTLES_PER_RESULT = 3;

    private static final String RANKED_BATTLE_TYPE = "pathOfLegend";
    // トロフィー目標(通常のトロフィー戦)。type は14,000未満が PvP、到達後が trail(trail にはイベント戦も入る)なので、モード名で見る。
    private static final Set<String> STANDARD_MODES = Set.of("Ladder");

    /** 勝った対戦・負けた対戦の中央値のどちらかがあるか。 */
    public boolean hasMedian() {
        return winMedian != null || loseMedian != null;
    }

    /** プレイヤーの対戦履歴(公式APIの battlelog)から。1対1の対戦では team の1人が本人。 */
    public static ElixirLeak ofPlayer(List<BattleLogEntry> battleLog) {
        List<Double> wins = new ArrayList<>();
        List<Double> losses = new ArrayList<>();
        for (BattleLogEntry battle : battleLog) {
            if (!isStandardOneOnOne(battle)) {
                continue;
            }
            Double leaked = battle.team().get(0).elixirLeaked();
            if (leaked == null) {
                continue;
            }
            switch (BattleResult.of(battle)) {
                case WIN -> wins.add(leaked);
                case LOSE -> losses.add(leaked);
                case DRAW -> {
                }
            }
        }
        return new ElixirLeak(wins.size() + losses.size(), median(wins), median(losses));
    }

    /** 世界上位どうしのランク戦から。1戦につき勝った側・負けた側の1人ずつを数える。 */
    public static ElixirLeak ofTopBattles(Collection<TopBattle> battles) {
        List<Double> wins = new ArrayList<>();
        List<Double> losses = new ArrayList<>();
        for (TopBattle battle : battles) {
            battle.winner().ifPresent(winner -> wins.add(winner.elixirLeaked()));
            battle.loser().ifPresent(loser -> losses.add(loser.elixirLeaked()));
        }
        // 引き分け以外の1戦に勝者と敗者が1人ずついるので、対戦の数は勝者の数。
        return new ElixirLeak(wins.size(), median(wins), median(losses));
    }

    static boolean isStandardOneOnOne(BattleLogEntry battle) {
        if (!BattleResult.hasBothSides(battle) || battle.team().size() != 1 || battle.opponent().size() != 1) {
            return false;
        }
        return RANKED_BATTLE_TYPE.equals(battle.type())
                || (battle.gameMode() != null && STANDARD_MODES.contains(battle.gameMode().name()));
    }

    private static Double median(List<Double> values) {
        if (values.size() < MIN_BATTLES_PER_RESULT) {
            return null;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }
}
