package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;

import java.util.Optional;
import java.util.Set;

/** 戦績サマリーと連勝・連敗の集計から外す対戦。画面の中で数字が食い違わないよう、両方で同じ基準を使う。 */
public enum BattleExclusion {

    /**
     * フレンドバトル。練習や身内の対戦で、同じ相手と続けて戦うことが多い。同じ8枚が何度も数えられて
     * 得意/苦手カードが実戦と関係ない結果になる(世界1位のプレイヤーで直近30戦中13戦を占めていた)。
     */
    FRIENDLY,

    /**
     * 船のバトルの守備側(他のクランに自分のクランの船を攻められた対戦)。船の防衛設備が戦い、本人は操作していない
     * (ゲーム内の説明も「オフラインでもクランの船は防衛される」)。放置中でも負けが並ぶため、連敗に数えると誤る。
     */
    BOAT_DEFENSE;

    // type の値は2026-09-27に42人・約1,150戦で確かめた。clanMate は全戦が同じクランの相手だった。
    // clanMate2v2(クランの仲間との2v2)は2026-10-04に20戦で確かめ、全戦が4人とも同じクランで、トロフィーの増減も無かった。
    // "unknown" の特殊ルール戦(RR_*_Friendly など)は名前に Friendly と付くが、相手は全戦が他クランで対象外。
    private static final Set<String> FRIENDLY_TYPES = Set.of("clanMate", "clanMate2v2", "friendly");

    public static Optional<BattleExclusion> of(BattleLogEntry battle) {
        if (FRIENDLY_TYPES.contains(battle.type())) {
            return Optional.of(FRIENDLY);
        }
        if ("defender".equals(battle.boatBattleSide())) {
            return Optional.of(BOAT_DEFENSE);
        }
        return Optional.empty();
    }
}
