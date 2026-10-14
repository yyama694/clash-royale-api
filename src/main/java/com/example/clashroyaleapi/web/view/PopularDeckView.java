package com.example.clashroyaleapi.web.view;

import java.util.List;

/**
 * デッキ画面の「人気のデッキ」の1件。カードと形・タワーユニットは、使っている人のうち最上位の人のもの。
 * レベルは人ごとに違うので出さない(cards の level は0)。cardIds はデッキ画面の絞り込みへのリンク用。
 */
public record PopularDeckView(int players, int topRank, String topName, String topPathTag, List<CardView> cards,
                              List<CardView> supportCards, DeckMetaView meta, List<Integer> cardIds) {
}
