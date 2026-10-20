package com.example.clashroyaleapi.web.view;

import java.util.List;

/**
 * 勝率の高いデッキ画面の1件。カードと形・タワーユニットは、使っている人のうち最上位の人の最も新しい対戦のもの。
 * レベルは人ごとに違うので出さない(cards の level は0)。
 *
 * @param firstBelowHalf 勝率50%未満のデッキのうち最初の1件。この前に区切りを出す
 */
public record WinRateDeckView(int winRatePercent, int battles, int wins, int players, int topRank, String topName,
                              String topPathTag, List<CardView> cards, List<CardView> supportCards, DeckMetaView meta,
                              boolean firstBelowHalf) {
}
