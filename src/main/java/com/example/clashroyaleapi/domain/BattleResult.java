package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;

import java.util.List;
import java.util.Locale;

/**
 * 対戦の勝敗。クラウン数の比較というルールをここ1箇所に閉じ込め、
 * 集計側(戦績サマリー)と表示側(対戦履歴・対戦詳細)とテンプレート側で同じ判定を書かないようにする。
 */
public enum BattleResult {

    WIN,
    LOSE,
    DRAW;

    public static BattleResult of(int selfCrowns, int opponentCrowns) {
        if (selfCrowns > opponentCrowns) {
            return WIN;
        }
        return selfCrowns < opponentCrowns ? LOSE : DRAW;
    }

    /**
     * 対戦のスコア。ふつうはクラウン数で、デュエル({@link #isDuel})では取ったラウンドの数。
     *
     * @param rounds デュエルのラウンドの勝ち数なら true
     */
    public record Score(int team, int opponent, boolean rounds) {
    }

    /** team 側(対戦履歴を見ているプレイヤーの側)から見た勝敗。先に {@link #hasBothSides} で確かめておくこと。 */
    public static BattleResult of(BattleLogEntry battle) {
        Score score = scoreOf(battle);
        return of(score.team(), score.opponent());
    }

    /**
     * デュエルは先に2ラウンドを取った方の勝ちで、公式APIの crowns(全ラウンドの合計)では決まらない
     * (合計5-4でラウンドは1勝2敗の負け、という対戦があった。2026-10-18)。先に {@link #hasBothSides} で確かめておくこと。
     */
    public static Score scoreOf(BattleLogEntry battle) {
        if (!isDuel(battle)) {
            return new Score(crownsOf(battle.team()), crownsOf(battle.opponent()), false);
        }
        List<BattleLogEntry.Round> mine = battle.team().get(0).rounds();
        List<BattleLogEntry.Round> theirs = battle.opponent().get(0).rounds();
        int won = 0;
        int lost = 0;
        for (int i = 0; i < Math.min(mine.size(), theirs.size()); i++) {
            BattleResult round = of(mine.get(i).crowns(), theirs.get(i).crowns());
            if (round == WIN) {
                won++;
            } else if (round == LOSE) {
                lost++;
            }
        }
        return new Score(won, lost, true);
    }

    /** ラウンドに分かれた1対1の対戦(クラン対戦のデュエル)か。 */
    public static boolean isDuel(BattleLogEntry battle) {
        return battle.team().size() == 1 && battle.opponent().size() == 1
                && hasRounds(battle.team().get(0)) && hasRounds(battle.opponent().get(0));
    }

    private static boolean hasRounds(BattleLogEntry.Participant participant) {
        return participant.rounds() != null && !participant.rounds().isEmpty();
    }

    /** 自分側と相手側の両方がそろった対戦か。片側が欠けた対戦は勝敗が決まらないため、一覧・集計・詳細のどれでも扱わない。 */
    public static boolean hasBothSides(BattleLogEntry battle) {
        return battle != null
                && battle.team() != null && !battle.team().isEmpty()
                && battle.opponent() != null && !battle.opponent().isEmpty();
    }

    // 2v2ではチームの各メンバーに同じクラウン数が入るが、仕様として保証されていないため最大値を取る。
    public static int crownsOf(List<BattleLogEntry.Participant> side) {
        return side.stream().mapToInt(BattleLogEntry.Participant::crowns).max().orElse(0);
    }

    /** 相手側から見た勝敗。 */
    public BattleResult opposite() {
        return switch (this) {
            case WIN -> LOSE;
            case LOSE -> WIN;
            case DRAW -> DRAW;
        };
    }

    /** メッセージキーとCSSクラス名の両方に使う小文字表記。 */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
