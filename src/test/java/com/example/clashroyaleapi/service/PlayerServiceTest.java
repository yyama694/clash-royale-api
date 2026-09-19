package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.client.exception.BattleNotFoundException;
import com.example.clashroyaleapi.client.exception.ResourceNotFoundException;
import com.example.clashroyaleapi.config.PlayerIndexProperties;
import com.example.clashroyaleapi.domain.PlayerNameMatch;
import com.example.clashroyaleapi.domain.PlayerSearchResult;
import com.example.clashroyaleapi.domain.PlayerSighting;
import com.example.clashroyaleapi.store.PlayerNameIndex;
import com.example.clashroyaleapi.store.PlayerSightingLog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerServiceTest {

    private ClashRoyaleApiClient apiClient;
    private PlayerSightingLog sightingLog;
    private PlayerNameIndex nameIndex;
    private PlayerService playerService;

    @BeforeEach
    void setUp() {
        apiClient = mock(ClashRoyaleApiClient.class);
        sightingLog = mock(PlayerSightingLog.class);
        nameIndex = mock(PlayerNameIndex.class);
        playerService = new PlayerService(apiClient, sightingLog, nameIndex, new PlayerIndexProperties("data", true));
    }

    @Test
    void 井桁付きはタグとして扱い公式APIにも索引にも問い合わせない() {
        assertEquals(new PlayerSearchResult.Found("#2ABC"), playerService.search(" #2abc "));

        verify(apiClient, never()).getPlayer(anyString());
        verify(nameIndex, never()).find(anyString());
    }

    @Test
    void タグの文字だけの入力はタグで見つかればそのプレイヤーにする() {
        PlayerResponse player = mock(PlayerResponse.class);
        when(player.tag()).thenReturn("#PYL");
        when(apiClient.getPlayer("PYL")).thenReturn(player);

        assertEquals(new PlayerSearchResult.Found("#PYL"), playerService.search("PYL"));
        verify(nameIndex, never()).find(anyString());
    }

    @Test
    void タグで見つからなければ名前として探し直す() {
        when(apiClient.getPlayer("PYL")).thenThrow(new ResourceNotFoundException("PYL", null));
        List<PlayerNameMatch> matches = List.of(new PlayerNameMatch("#AAA", "pyl", Instant.EPOCH));
        when(nameIndex.find("PYL")).thenReturn(matches);

        assertEquals(new PlayerSearchResult.Candidates(matches, 1), playerService.search("PYL"));
    }

    @Test
    void タグに使われない文字を含む入力は公式APIに問い合わせず名前で探す() {
        when(nameIndex.find("bob")).thenReturn(List.of());

        assertEquals(new PlayerSearchResult.NotFound("bob"), playerService.search("bob"));
        verify(apiClient, never()).getPlayer(anyString());
    }

    @Test
    void 名前の候補は上限までに絞り件数は絞る前の数を返す() {
        List<PlayerNameMatch> matches = IntStream.range(0, 60)
                .mapToObj(i -> new PlayerNameMatch("#T" + i, "bob", Instant.EPOCH))
                .toList();
        when(nameIndex.find("bob")).thenReturn(matches);

        PlayerSearchResult.Candidates result = (PlayerSearchResult.Candidates) playerService.search("bob");

        assertEquals(50, result.players().size());
        assertEquals(60, result.total());
    }

    @Test
    void 名前検索が無効なら入力をすべてタグとして扱う() {
        PlayerService disabled = new PlayerService(apiClient, sightingLog, nameIndex,
                new PlayerIndexProperties("data", false));

        assertEquals(new PlayerSearchResult.Found("#B0B"), disabled.search("bob"));
        verify(nameIndex, never()).find(anyString());
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
