package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.exception.BattleNotFoundException;
import com.example.clashroyaleapi.domain.PlayerSighting;
import com.example.clashroyaleapi.store.PlayerSightingLog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerServiceTest {

    private ClashRoyaleApiClient apiClient;
    private PlayerSightingLog sightingLog;
    private PlayerService playerService;

    @BeforeEach
    void setUp() {
        apiClient = mock(ClashRoyaleApiClient.class);
        sightingLog = mock(PlayerSightingLog.class);
        playerService = new PlayerService(apiClient, sightingLog);
    }

    @Test
    void 対戦は位置ではなくbattleTimeで特定する() {
        // 新しい対戦が入ってbattlelogの並びがずれても、同じURLが同じ対戦を指し続けることの確認。
        when(apiClient.getBattleLog(anyString())).thenReturn(List.of(
                battle("20260102T000000.000Z"),
                battle("20260101T000000.000Z")));

        BattleLogEntry battle = playerService.findBattle("#TAG", "20260101T000000.000Z");

        assertEquals("20260101T000000.000Z", battle.battleTime());
    }

    @Test
    void 該当するbattleTimeがなければ見つからない扱いにする() {
        when(apiClient.getBattleLog(anyString())).thenReturn(List.of(battle("20260102T000000.000Z")));

        assertThrows(BattleNotFoundException.class,
                () -> playerService.findBattle("#TAG", "20250101T000000.000Z"));
    }

    @Test
    void 対戦履歴に出てきた自分と対戦相手を記録する() {
        when(apiClient.getBattleLog(anyString())).thenReturn(List.of(battle("20260101T000000.000Z")));

        playerService.findBattleLog("#SELF");

        verify(sightingLog).record(List.of(new PlayerSighting("#SELF", "Self"), new PlayerSighting("#OPP", "Opp")));
    }

    private static BattleLogEntry battle(String battleTime) {
        BattleLogEntry.Participant self = new BattleLogEntry.Participant("#SELF", "Self", 3, List.of(), List.of());
        BattleLogEntry.Participant opponent = new BattleLogEntry.Participant("#OPP", "Opp", 0, List.of(), List.of());
        return new BattleLogEntry("PvP", battleTime, new BattleLogEntry.GameMode("Ladder"),
                List.of(self), List.of(opponent));
    }
}
