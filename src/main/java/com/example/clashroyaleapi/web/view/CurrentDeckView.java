package com.example.clashroyaleapi.web.view;

import java.util.List;

/** 使用中のデッキ。ゲーム内で今セットしているデッキ(公式APIの currentDeck)。 */
public record CurrentDeckView(List<CardView> cards, List<CardView> supportCards, DeckMetaView meta) {
}
