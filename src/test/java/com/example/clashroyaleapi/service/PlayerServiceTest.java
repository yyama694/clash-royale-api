package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.client.exception.BattleNotFoundException;
import com.example.clashroyaleapi.client.exception.ResourceNotFoundException;
import com.example.clashroyaleapi.config.PlayerIndexProperties;
import com.example.clashroyaleapi.domain.PlayerNameMatch;
import com.example.clashroyaleapi.domain.PlayerNameSearch;
import com.example.clashroyaleapi.domain.PlayerSearchResult;
import com.example.clashroyaleapi.domain.PlayerSighting;
import com.example.clashroyaleapi.store.PlayerNameIndex;
import com.example.clashroyaleapi.store.PlayerSightingLog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
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

    @Test
    void 同名の候補が2人以上なら先頭の20人だけ今のプロフィールを取り_取れない人は除く() {
        List<PlayerNameMatch> exact = IntStream.range(0, 25)
                .mapToObj(i -> new PlayerNameMatch("#P" + i, "てち", Instant.EPOCH))
                .toList();
        PlayerResponse player = mock(PlayerResponse.class);
        when(player.tag()).thenReturn("#X");
        when(player.name()).thenReturn("てち");
        when(apiClient.getPlayer(anyString())).thenReturn(player);
        when(apiClient.getPlayer("#P3")).thenThrow(new ResourceNotFoundException("gone", null));

        Map<String, PlayerResponse> details =
                playerService.candidateDetails(new PlayerNameSearch(exact, 25, 0, List.of(), false));

        assertEquals(PlayerService.CANDIDATE_DETAILS_LIMIT - 1, details.size());
        assertTrue(details.containsKey("#P0"));
        assertFalse(details.containsKey("#P3"));
        verify(apiClient, never()).getPlayer("#P20");
    }

    @Test
    void 候補が1人なら見分ける必要が無いのでプロフィールを取らない() {
        PlayerNameSearch single = new PlayerNameSearch(
                List.of(new PlayerNameMatch("#AAA", "pyl", Instant.EPOCH)), 1, 0, List.of(), false);

        assertTrue(playerService.candidateDetails(single).isEmpty());
        verify(apiClient, never()).getPlayer(anyString());
    }

    @BeforeEach
    void setUp() {
        apiClient = mock(ClashRoyaleApiClient.class);
        sightingLog = mock(PlayerSightingLog.class);
        nameIndex = mock(PlayerNameIndex.class);
        playerService = new PlayerService(apiClient, sightingLog, nameIndex, new PlayerIndexProperties("data", true));
    }

    @Test
    void 井桁付きはタグとして扱い公式APIにも索引にも問い合わせない() {
        assertEquals(new PlayerSearchResult.Found("#2ABC"), playerService.search(" #2abc ", 1));

        verify(apiClient, never()).getPlayer(anyString());
        verify(nameIndex, never()).search(anyString(), anyInt(), anyInt());
    }

    @Test
    void 全角で打った井桁付きのタグもタグとして扱う() {
        assertEquals(new PlayerSearchResult.Found("#2ABC"), playerService.search("＃２ａｂｃ", 1));

        verify(nameIndex, never()).search(anyString(), anyInt(), anyInt());
    }

    @Test
    void 全角で打ったタグの文字だけの入力は半角にして公式APIに問い合わせる() {
        PlayerResponse player = mock(PlayerResponse.class);
        when(player.tag()).thenReturn("#PYL");
        when(apiClient.getPlayer("PYL")).thenReturn(player);

        assertEquals(new PlayerSearchResult.Found("#PYL"), playerService.search("ＰＹＬ", 1));
        verify(nameIndex, never()).search(anyString(), anyInt(), anyInt());
    }

    @Test
    void プロフィールの共有リンクはタグとして扱う() {
        assertEquals(new PlayerSearchResult.Found("#2VG8GV9UV"), playerService.search(
                "https://link.clashroyale.com/invite/friend/ja?tag=2VG8GV9UV&token=abc12345&platform=iOS", 1));

        verify(nameIndex, never()).search(anyString(), anyInt(), anyInt());
    }

    @Test
    void 全角の井桁で始まる名前は名前として探す() {
        PlayerNameSearch empty = new PlayerNameSearch(List.of(), 0, 0, List.of(), false);
        when(nameIndex.search("＃てち", 0, 50)).thenReturn(empty);

        assertEquals(new PlayerSearchResult.NotFound("＃てち"), playerService.search("＃てち", 1));
    }

    @Test
    void タグの文字だけの入力はタグで見つかればそのプレイヤーにする() {
        PlayerResponse player = mock(PlayerResponse.class);
        when(player.tag()).thenReturn("#PYL");
        when(apiClient.getPlayer("PYL")).thenReturn(player);

        assertEquals(new PlayerSearchResult.Found("#PYL"), playerService.search("PYL", 1));
        verify(nameIndex, never()).search(anyString(), anyInt(), anyInt());
    }

    @Test
    void タグで見つからなければ名前として探し直す() {
        when(apiClient.getPlayer("PYL")).thenThrow(new ResourceNotFoundException("PYL", null));
        PlayerNameSearch found = new PlayerNameSearch(
                List.of(new PlayerNameMatch("#AAA", "pyl", Instant.EPOCH)), 1, 0, List.of(), false);
        when(nameIndex.search("PYL", 0, 50)).thenReturn(found);

        assertEquals(new PlayerSearchResult.Candidates(found), playerService.search("PYL", 1));
    }

    @Test
    void タグに使われない文字を含む入力は公式APIに問い合わせず名前で探す() {
        when(nameIndex.search("bob", 0, 50)).thenReturn(new PlayerNameSearch(List.of(), 0, 0, List.of(), false));

        assertEquals(new PlayerSearchResult.NotFound("bob"), playerService.search("bob", 1));
        verify(apiClient, never()).getPlayer(anyString());
    }

    @Test
    void ページ番号から読み始める位置を決め_範囲外なら最後のページにする() {
        PlayerNameSearch empty = new PlayerNameSearch(List.of(), 120, 500, List.of(), false);
        PlayerNameSearch last = new PlayerNameSearch(
                List.of(new PlayerNameMatch("#T", "bob", Instant.EPOCH)), 120, 100, List.of(), false);
        when(nameIndex.search("bob", 500, 50)).thenReturn(empty);
        when(nameIndex.search("bob", 100, 50)).thenReturn(last);

        assertEquals(new PlayerSearchResult.Candidates(last), playerService.search("bob", 11));
    }

    @Test
    void 大きすぎるページ番号でも溢れずに最後のページにする() {
        int maxOffset = Integer.MAX_VALUE - 50;
        PlayerNameSearch empty = new PlayerNameSearch(List.of(), 120, maxOffset, List.of(), false);
        PlayerNameSearch last = new PlayerNameSearch(
                List.of(new PlayerNameMatch("#T", "bob", Instant.EPOCH)), 120, 100, List.of(), false);
        when(nameIndex.search("bob", maxOffset, 50)).thenReturn(empty);
        when(nameIndex.search("bob", 100, 50)).thenReturn(last);

        assertEquals(new PlayerSearchResult.Candidates(last), playerService.search("bob", Integer.MAX_VALUE));
    }

    @Test
    void 名前検索が無効なら入力をすべてタグとして扱う() {
        PlayerService disabled = new PlayerService(apiClient, sightingLog, nameIndex,
                new PlayerIndexProperties("data", false));

        assertEquals(new PlayerSearchResult.Found("#B0B"), disabled.search("bob", 1));
        verify(nameIndex, never()).search(anyString(), anyInt(), anyInt());
    }

    @Test
    void タグとして扱う入力がタグの形式でなければ転送せず見つからないにする() {
        PlayerService disabled = new PlayerService(apiClient, sightingLog, nameIndex,
                new PlayerIndexProperties("data", false));

        assertEquals(new PlayerSearchResult.NotFound("#ab/c"), playerService.search("#ab/c", 1));
        assertEquals(new PlayerSearchResult.NotFound("ab/c"), disabled.search("ab/c", 1));
        verify(apiClient, never()).getPlayer(anyString());
    }

    @Test
    void 対戦は位置ではなくbattleTimeで特定する() {
        // 新しい対戦が入ってbattlelogの並びがずれても、同じURLが同じ対戦を指し続けることの確認。
        when(apiClient.getBattleLog(anyString())).thenReturn(List.of(
                battle("20260102T000000.000Z"),
                battle("20260101T000000.000Z")));

        BattleLogEntry battle = playerService.findBattle("#TAG", "20260101T000000.000Z").battle();

        assertEquals("20260101T000000.000Z", battle.battleTime());
    }

    @Test
    void 該当するbattleTimeがなければ見つからない扱いにする() {
        when(apiClient.getBattleLog(anyString())).thenReturn(List.of(battle("20260102T000000.000Z")));

        assertThrows(BattleNotFoundException.class,
                () -> playerService.findBattle("#TAG", "20250101T000000.000Z"));
    }

    @Test
    void 片側が欠けた対戦は一覧に出さないのでbattleTimeを指定されても見つからない扱いにする() {
        BattleLogEntry.Participant self = new BattleLogEntry.Participant("#SELF", "Self", 3, List.of(), List.of(), null);
        when(apiClient.getBattleLog(anyString())).thenReturn(List.of(new BattleLogEntry("PvP",
                "20260101T000000.000Z", new BattleLogEntry.GameMode("Ladder"), List.of(self), List.of(), null)));

        assertThrows(BattleNotFoundException.class,
                () -> playerService.findBattle("#TAG", "20260101T000000.000Z"));
    }

    @Test
    void 対戦履歴に出てきた自分と対戦相手を記録する() {
        when(apiClient.getBattleLog(anyString())).thenReturn(List.of(battle("20260101T000000.000Z")));

        playerService.findBattleLog("#SELF");

        verify(sightingLog).record(List.of(new PlayerSighting("#SELF", "Self"), new PlayerSighting("#OPP", "Opp")));
    }

    private static BattleLogEntry battle(String battleTime) {
        BattleLogEntry.Participant self = new BattleLogEntry.Participant("#SELF", "Self", 3, List.of(), List.of(), null);
        BattleLogEntry.Participant opponent = new BattleLogEntry.Participant("#OPP", "Opp", 0, List.of(), List.of(), null);
        return new BattleLogEntry("PvP", battleTime, new BattleLogEntry.GameMode("Ladder"),
                List.of(self), List.of(opponent), null);
    }
}
