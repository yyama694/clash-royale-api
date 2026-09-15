package com.example.clashroyaleapi.web.view;

import com.example.clashroyaleapi.domain.BattleResult;

import java.util.List;

public record ParticipantView(String tag, String pathTag, String name, int crowns, BattleResult result,
        List<CardView> cards, List<CardView> supportCards) {
}
