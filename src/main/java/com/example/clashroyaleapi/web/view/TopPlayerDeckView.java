package com.example.clashroyaleapi.web.view;

import java.util.List;

/** トッププレイヤーのデッキ画面の1行。rank・rating は集計した時点のランキングの値。 */
public record TopPlayerDeckView(int rank, String name, String pathTag, int rating, List<CardView> cards,
                                List<CardView> supportCards, DeckMetaView meta) {
}
