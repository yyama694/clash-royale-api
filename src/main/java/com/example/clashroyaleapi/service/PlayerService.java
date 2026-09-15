package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.client.exception.BattleNotFoundException;
import com.example.clashroyaleapi.domain.PlayerBattleStats;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PlayerService {

    private final ClashRoyaleApiClient apiClient;

    public PlayerService(ClashRoyaleApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public PlayerResponse findPlayer(String tag) {
        return apiClient.getPlayer(tag);
    }

    public List<BattleLogEntry> findBattleLog(String tag) {
        return apiClient.getBattleLog(tag);
    }

    public PlayerBattleStats statsOf(List<BattleLogEntry> battleLog) {
        return PlayerBattleStats.from(battleLog);
    }

    /**
     * battlelogは新しい対戦が入るたびに先頭へ積まれるため、位置(index)で参照すると
     * 画面を開き直した際に黙って別の対戦が表示されてしまう。battleTimeをキーにして特定する。
     */
    public BattleLogEntry findBattle(String tag, String battleTime) {
        Optional<BattleLogEntry> battle = apiClient.getBattleLog(tag).stream()
                .filter(entry -> battleTime.equals(entry.battleTime()))
                .findFirst();
        return battle.orElseThrow(() -> new BattleNotFoundException("battle " + battleTime + " of " + tag));
    }
}
