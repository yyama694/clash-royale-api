package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.client.exception.ApiUnavailableException;
import com.example.clashroyaleapi.client.exception.ResourceNotFoundException;
import com.example.clashroyaleapi.domain.FavoriteFetch;
import com.example.clashroyaleapi.domain.Favorites;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FavoriteServiceTest {

    private PlayerService playerService;
    private ClanService clanService;
    private FavoriteService favoriteService;

    @BeforeEach
    void setUp() {
        playerService = mock(PlayerService.class);
        clanService = mock(ClanService.class);
        favoriteService = new FavoriteService(playerService, clanService);
    }

    @Test
    void 一部の件だけ404や接続失敗になっても残りは取得できて登録順が保たれる() {
        when(playerService.findPlayer("AAA")).thenReturn(player("AAA", "太郎"));
        when(playerService.findPlayer("BBB")).thenThrow(new ResourceNotFoundException("not found", null));
        when(playerService.findPlayer("CCC")).thenThrow(new ApiUnavailableException("boom", null));
        List<Favorites.Entry> entries = List.of(
                new Favorites.Entry("AAA", "太郎"),
                new Favorites.Entry("BBB", "次郎"),
                new Favorites.Entry("CCC", "三郎"));

        List<FavoriteFetch<PlayerResponse>> results = favoriteService.fetchPlayers(entries);

        assertEquals(3, results.size());
        assertEquals("AAA", results.get(0).tag());
        assertInstanceOf(FavoriteFetch.Found.class, results.get(0));
        assertEquals("BBB", results.get(1).tag());
        assertInstanceOf(FavoriteFetch.NotFound.class, results.get(1));
        assertEquals("CCC", results.get(2).tag());
        assertInstanceOf(FavoriteFetch.Unavailable.class, results.get(2));
    }

    @Test
    void プレイヤーが0件なら取得しに行かない() {
        assertEquals(List.of(), favoriteService.fetchPlayers(List.of()));
    }

    @Test
    void クランも取得できる() {
        when(clanService.findClan("XXX")).thenReturn(clan("XXX", "償い"));

        List<FavoriteFetch<ClanResponse>> results = favoriteService.fetchClans(
                List.of(new Favorites.Entry("XXX", "償い")));

        assertEquals(1, results.size());
        assertInstanceOf(FavoriteFetch.Found.class, results.get(0));
        assertEquals("償い", ((FavoriteFetch.Found<ClanResponse>) results.get(0)).value().name());
    }

    private static PlayerResponse player(String tag, String name) {
        return new PlayerResponse(tag, name, 1, 0, 0, 0, 0, 0, null, List.of(), List.of(), null, null, null,
                List.of());
    }

    private static ClanResponse clan(String tag, String name) {
        return new ClanResponse(tag, name, "", 0, 0, 0, List.of());
    }
}
