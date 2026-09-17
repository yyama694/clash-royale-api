package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.web.view.BattleDetailView;
import com.example.clashroyaleapi.web.view.BattleSummaryView;
import com.example.clashroyaleapi.web.view.ParticipantView;
import com.example.clashroyaleapi.web.view.PlayerLinkView;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 2v2で「自分」と「味方」を取り違えない並べ方を中心に検証する。ラベル解決はモックで素通しにする。 */
class ViewMapperTest {

    private ViewMapper viewMapper;

    @BeforeEach
    void setUp() {
        LabelResolver labels = mock(LabelResolver.class);
        when(labels.gameMode(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(labels.cardName(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        viewMapper = new ViewMapper(labels);
    }

    @Test
    void 対戦詳細では見ているプレイヤーを先頭にし味方と区別する() {
        // 公式APIのteamは、見ているプレイヤーが先頭とは限らない(実データで味方が先頭の2v2があった)。
        BattleLogEntry duel = duel(List.of(participant("#MATE", "Mate"), participant("#VIEWER", "Viewer")));

        BattleDetailView detail = viewMapper.toBattleDetail(duel, "viewer", Locale.JAPANESE);

        List<ParticipantView> team = detail.team();
        assertEquals("Viewer", team.get(0).name());
        assertTrue(team.get(0).viewer());
        assertEquals("Mate", team.get(1).name());
        assertFalse(team.get(1).viewer());
        assertTrue(detail.opponents().stream().noneMatch(ParticipantView::viewer));
    }

    @Test
    void 対戦履歴では相手を全員出し見ているプレイヤー以外を味方にする() {
        BattleLogEntry duel = duel(List.of(participant("#MATE", "Mate"), participant("#VIEWER", "Viewer")));

        BattleSummaryView summary = viewMapper.toBattleSummaries(List.of(duel), "#VIEWER", Locale.JAPANESE).get(0);

        assertEquals(List.of("Opp1", "Opp2"), summary.opponents().stream().map(PlayerLinkView::name).toList());
        assertEquals(List.of("Mate"), summary.teammates().stream().map(PlayerLinkView::name).toList());
    }

    @Test
    void 表示する名前からは色指定タグを取り除く() {
        BattleLogEntry battle = new BattleLogEntry("PvP", "20260101T000000.000Z",
                new BattleLogEntry.GameMode("Ladder"),
                List.of(participant("#VIEWER", "Viewer")), List.of(participant("#OPP1", "<c6>Ale :D")));

        BattleSummaryView summary = viewMapper.toBattleSummaries(List.of(battle), "#VIEWER", Locale.JAPANESE).get(0);

        assertEquals("Ale :D", summary.opponents().get(0).name());
        assertTrue(summary.teammates().isEmpty());
    }

    private static BattleLogEntry duel(List<BattleLogEntry.Participant> team) {
        return new BattleLogEntry("PvP", "20260101T000000.000Z", new BattleLogEntry.GameMode("TeamVsTeam"),
                team, List.of(participant("#OPP1", "Opp1"), participant("#OPP2", "Opp2")));
    }

    private static BattleLogEntry.Participant participant(String tag, String name) {
        return new BattleLogEntry.Participant(tag, name, 1, List.of(), List.of());
    }
}
