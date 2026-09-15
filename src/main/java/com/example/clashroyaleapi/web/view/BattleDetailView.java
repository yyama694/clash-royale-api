package com.example.clashroyaleapi.web.view;

import java.util.List;

/** 対戦詳細。2v2では team / opponents が2人ずつになるためリストで持つ。 */
public record BattleDetailView(String battleTime, String gameMode, List<ParticipantView> team,
        List<ParticipantView> opponents) {
}
