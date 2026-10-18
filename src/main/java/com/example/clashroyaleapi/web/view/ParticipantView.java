package com.example.clashroyaleapi.web.view;

import com.example.clashroyaleapi.domain.BattleResult;

import java.util.List;

/**
 * viewer は、対戦詳細を開いているプレイヤー本人かどうか(2v2で「自分」と「味方」を分けるために使う)。
 * elixirLeaked は消費漏れ。公式APIが返さなかった対戦と、船のバトル(本人が操作しない守備側があり、ほぼ0になる)では null。
 */
public record ParticipantView(String tag, String pathTag, String name, int crowns, BattleResult result,
        List<CardView> cards, List<CardView> supportCards, DeckMetaView meta, boolean viewer,
        TrophyChangeView trophyChange, Double elixirLeaked) {
}
