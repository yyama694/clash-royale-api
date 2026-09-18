package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.exception.BattleNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerServiceTest {

    private ClashRoyaleApiClient apiClient;
    private PlayerService playerService;

    @BeforeEach
    void setUp() {
        apiClient = mock(ClashRoyaleApiClient.class);
        playerService = new PlayerService(apiClient);
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
    void 使用中のデッキは2v2を飛ばして直近の1vs1から取る() {
        List<BattleLogEntry> battleLog = List.of(
                battle("20260103T000000.000Z", 2, 8),
                battle("20260102T000000.000Z", 1, 8),
                battle("20260101T000000.000Z", 1, 8));

        Optional<BattleLogEntry> battle = playerService.latestOneOnOne(battleLog);

        assertEquals("20260102T000000.000Z", battle.orElseThrow().battleTime());
    }

    @Test
    void デッキが8枚そろっていない1vs1は使用中のデッキにしない() {
        List<BattleLogEntry> battleLog = List.of(
                battle("20260102T000000.000Z", 1, 0),
                battle("20260101T000000.000Z", 1, 8));

        assertEquals("20260101T000000.000Z", playerService.latestOneOnOne(battleLog).orElseThrow().battleTime());
    }

    @Test
    void 直近に1vs1が無ければ使用中のデッキは無し() {
        assertTrue(playerService.latestOneOnOne(List.of(battle("20260101T000000.000Z", 2, 8))).isEmpty());
    }

    private static BattleLogEntry battle(String battleTime, int teamSize, int deckSize) {
        List<BattleLogEntry.Card> cards = Collections.nCopies(deckSize, new BattleLogEntry.Card("Knight", 11, null));
        List<BattleLogEntry.Participant> team = Collections.nCopies(teamSize,
                new BattleLogEntry.Participant("#SELF", "Self", 1, cards, List.of()));
        List<BattleLogEntry.Participant> opponent = Collections.nCopies(teamSize,
                new BattleLogEntry.Participant("#OPP", "Opp", 0, cards, List.of()));
        return new BattleLogEntry("PvP", battleTime, new BattleLogEntry.GameMode("Ladder"), team, opponent);
    }

    private static BattleLogEntry battle(String battleTime) {
        BattleLogEntry.Participant self = new BattleLogEntry.Participant("#SELF", "Self", 3, List.of(), List.of());
        BattleLogEntry.Participant opponent = new BattleLogEntry.Participant("#OPP", "Opp", 0, List.of(), List.of());
        return new BattleLogEntry("PvP", battleTime, new BattleLogEntry.GameMode("Ladder"),
                List.of(self), List.of(opponent));
    }
}
