package com.example.clashroyaleapi.web.view;

import com.example.clashroyaleapi.domain.BattleResult;

import java.util.List;

/** viewer は、対戦詳細を開いているプレイヤー本人かどうか(2v2で「自分」と「味方」を分けるために使う)。 */
public record ParticipantView(String tag, String pathTag, String name, int crowns, BattleResult result,
        List<CardView> cards, List<CardView> supportCards, DeckMetaView meta, boolean viewer) {
}
