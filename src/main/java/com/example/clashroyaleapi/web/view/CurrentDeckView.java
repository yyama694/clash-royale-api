package com.example.clashroyaleapi.web.view;

import java.util.List;

/** 使用中のデッキ。どの対戦から取ったかを画面に出せるよう、元の対戦の日時とモードも持つ。 */
public record CurrentDeckView(String battleTime, TimeView time, String gameMode, List<CardView> cards,
        List<CardView> supportCards, DeckMetaView meta) {
}
