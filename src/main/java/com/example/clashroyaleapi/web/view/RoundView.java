package com.example.clashroyaleapi.web.view;

import com.example.clashroyaleapi.domain.BattleResult;

import java.util.List;

/**
 * デュエルの1ラウンド(対戦詳細)。crowns・opponentCrowns・result は、このラウンドを出している参加者の側から見たもの。
 * elixirLeaked は消費漏れで、公式APIが返さなければ null。
 */
public record RoundView(int number, int crowns, int opponentCrowns, BattleResult result, List<CardView> cards,
        DeckMetaView meta, Double elixirLeaked) {
}
