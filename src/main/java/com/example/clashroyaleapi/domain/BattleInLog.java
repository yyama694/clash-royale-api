package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;

import java.util.List;
import java.util.Optional;

/**
 * 対戦履歴の中の1戦と、対戦履歴の一覧で隣にある対戦(対戦詳細画面の「前の対戦」「次の対戦」)。
 * 一覧に出さない片側が欠けた対戦は飛ばす。隣が無ければ null。
 */
public record BattleInLog(BattleLogEntry battle, String olderBattleTime, String newerBattleTime) {

    /** battleLog は公式APIの順(新しい順)。 */
    public static Optional<BattleInLog> find(List<BattleLogEntry> battleLog, String battleTime) {
        List<BattleLogEntry> listed = battleLog.stream().filter(BattleResult::hasBothSides).toList();
        for (int i = 0; i < listed.size(); i++) {
            if (battleTime.equals(listed.get(i).battleTime())) {
                return Optional.of(new BattleInLog(listed.get(i),
                        i + 1 < listed.size() ? listed.get(i + 1).battleTime() : null,
                        i > 0 ? listed.get(i - 1).battleTime() : null));
            }
        }
        return Optional.empty();
    }
}
