package com.example.clashroyaleapi.web.view;

import java.util.List;

/**
 * 対戦詳細。2v2では team / opponents が2人ずつになるためリストで持つ。
 * battleTime は公式APIの生の値(URLのキーに使う)、time は表示用。
 */
public record BattleDetailView(String battleTime, TimeView time, String gameMode, List<ParticipantView> team,
        List<ParticipantView> opponents) {
}
