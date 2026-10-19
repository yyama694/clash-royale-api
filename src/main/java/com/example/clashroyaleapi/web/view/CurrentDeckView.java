package com.example.clashroyaleapi.web.view;

import java.util.List;

/**
 * 使用中のデッキ。直近のトロフィー戦・ランク戦で使ったデッキで、無ければ公式APIの currentDeck(CurrentDeck 参照)。
 * completedFromBattle は、currentDeck が返さなかったカードを直近の対戦から補ったとき true。
 */
public record CurrentDeckView(List<CardView> cards, List<CardView> supportCards, DeckMetaView meta,
                              boolean completedFromBattle) {
}
