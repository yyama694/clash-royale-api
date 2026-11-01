package com.example.clashroyaleapi.client.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerResponseTest {

    private static PlayerResponse player(PlayerResponse.RankedSeasonResult current,
            PlayerResponse.RankedSeasonResult last) {
        return new PlayerResponse("#AAA", "太郎", 16, 14000, 14000, 900, 400, 100, null, List.of(), List.of(),
                current, last, null, List.of());
    }

    @Test
    void 前のシーズンと同じ順位は今シーズンの順位として扱わない() {
        // 2026-11-01の実例: レーティング2,247なのに、前のシーズン(2,949)の695位が今シーズンの順位として返った
        PlayerResponse player = player(new PlayerResponse.RankedSeasonResult(7, 2247, 695),
                new PlayerResponse.RankedSeasonResult(7, 2949, 695));

        PlayerResponse.RankedSeasonResult current = player.currentPathOfLegendSeasonResult();
        assertNull(current.rank());
        assertFalse(current.hasRank());
        assertEquals(7, current.leagueNumber());
        assertEquals(2247, current.trophies());
    }

    @Test
    void 前のシーズンと違う順位は今シーズンの順位として残す() {
        PlayerResponse player = player(new PlayerResponse.RankedSeasonResult(7, 3259, 23),
                new PlayerResponse.RankedSeasonResult(7, 3721, 17));

        assertEquals(23, player.currentPathOfLegendSeasonResult().rank());
        assertTrue(player.currentPathOfLegendSeasonResult().hasRank());
    }

    @Test
    void 前のシーズンの順位が無ければ今シーズンの順位を残す() {
        PlayerResponse noLastRank = player(new PlayerResponse.RankedSeasonResult(7, 3100, 25),
                new PlayerResponse.RankedSeasonResult(4, 0, null));
        PlayerResponse noLastResult = player(new PlayerResponse.RankedSeasonResult(7, 3100, 25), null);

        assertEquals(25, noLastRank.currentPathOfLegendSeasonResult().rank());
        assertEquals(25, noLastResult.currentPathOfLegendSeasonResult().rank());
    }

    @Test
    void 今シーズンの成績が無ければそのまま() {
        PlayerResponse player = player(null, new PlayerResponse.RankedSeasonResult(7, 2949, 695));

        assertNull(player.currentPathOfLegendSeasonResult());
    }
}
