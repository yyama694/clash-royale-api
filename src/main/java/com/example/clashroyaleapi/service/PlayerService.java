package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.client.exception.BattleNotFoundException;
import com.example.clashroyaleapi.domain.PlayerBattleStats;
import com.example.clashroyaleapi.domain.PlayerSighting;
import com.example.clashroyaleapi.store.PlayerSightingLog;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class PlayerService {

    private static final int DECK_SIZE = 8;

    private final ClashRoyaleApiClient apiClient;
    private final PlayerSightingLog sightingLog;

    public PlayerService(ClashRoyaleApiClient apiClient, PlayerSightingLog sightingLog) {
        this.apiClient = apiClient;
        this.sightingLog = sightingLog;
    }

    public PlayerResponse findPlayer(String tag) {
        PlayerResponse player = apiClient.getPlayer(tag);
        sightingLog.record(List.of(new PlayerSighting(player.tag(), player.name())));
        return player;
    }

    public List<BattleLogEntry> findBattleLog(String tag) {
        List<BattleLogEntry> battleLog = apiClient.getBattleLog(tag);
        sightingLog.record(participantsOf(battleLog));
        return battleLog;
    }

    private static List<PlayerSighting> participantsOf(List<BattleLogEntry> battleLog) {
        return battleLog.stream()
                .flatMap(battle -> Stream.of(battle.team(), battle.opponent()))
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(participant -> new PlayerSighting(participant.tag(), participant.name()))
                .toList();
    }

    public PlayerBattleStats statsOf(List<BattleLogEntry> battleLog) {
        return PlayerBattleStats.from(battleLog);
    }

    /**
     * 使用中のデッキの代わりに、直近の1vs1で使ったデッキを返すための対戦を選ぶ(battlelogは新しい順)。
     * 2v2と、デッキが8枚そろっていない対戦(ボートバトルの防衛側など)は、普段のデッキとは言えないので除く。
     */
    public Optional<BattleLogEntry> latestOneOnOne(List<BattleLogEntry> battleLog) {
        return battleLog.stream()
                .filter(PlayerService::isOneOnOneWithFullDeck)
                .findFirst();
    }

    private static boolean isOneOnOneWithFullDeck(BattleLogEntry battle) {
        return battle.team() != null && battle.team().size() == 1
                && battle.opponent() != null && battle.opponent().size() == 1
                && battle.team().get(0).cards() != null && battle.team().get(0).cards().size() == DECK_SIZE;
    }

    /**
     * battlelogは新しい対戦が入るたびに先頭へ積まれるため、位置(index)で参照すると
     * 画面を開き直した際に黙って別の対戦が表示されてしまう。battleTimeをキーにして特定する。
     */
    public BattleLogEntry findBattle(String tag, String battleTime) {
        Optional<BattleLogEntry> battle = findBattleLog(tag).stream()
                .filter(entry -> battleTime.equals(entry.battleTime()))
                .findFirst();
        return battle.orElseThrow(() -> new BattleNotFoundException("battle " + battleTime + " of " + tag));
    }
}
