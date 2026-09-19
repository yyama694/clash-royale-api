package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.client.exception.BattleNotFoundException;
import com.example.clashroyaleapi.client.exception.ResourceNotFoundException;
import com.example.clashroyaleapi.config.PlayerIndexProperties;
import com.example.clashroyaleapi.domain.PlayerBattleStats;
import com.example.clashroyaleapi.domain.PlayerNameSearch;
import com.example.clashroyaleapi.domain.PlayerSearchResult;
import com.example.clashroyaleapi.domain.PlayerSighting;
import com.example.clashroyaleapi.store.PlayerNameIndex;
import com.example.clashroyaleapi.store.PlayerSightingLog;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class PlayerService {

    // 名前検索の1ページの人数。よくある名前は完全一致だけで数百人以上になるため、ページ送りにする。
    public static final int SEARCH_PAGE_SIZE = 50;

    private final ClashRoyaleApiClient apiClient;
    private final PlayerSightingLog sightingLog;
    private final PlayerNameIndex nameIndex;
    private final boolean nameSearchEnabled;

    public PlayerService(ClashRoyaleApiClient apiClient, PlayerSightingLog sightingLog, PlayerNameIndex nameIndex,
            PlayerIndexProperties properties) {
        this.apiClient = apiClient;
        this.sightingLog = sightingLog;
        this.nameIndex = nameIndex;
        this.nameSearchEnabled = properties.nameSearchEnabled();
    }

    /**
     * プレイヤータグ・名前のどちらでも検索できるようにする。
     * 「#」付きはタグとしか読めないので、存在確認はプレイヤー情報画面に任せる(無ければそこで見つからない旨を出す)。
     * タグの文字だけでできた入力は名前の可能性もあるため、タグで見つからなければ名前として探し直す。
     * page は名前が完全一致した人のページ番号(1始まり)。範囲外なら最後のページにする。
     */
    public PlayerSearchResult search(String query, int page) {
        String trimmed = query == null ? "" : query.strip();
        if (trimmed.isEmpty()) {
            return new PlayerSearchResult.NotFound("");
        }
        if (!nameSearchEnabled || trimmed.startsWith("#")) {
            // "/"などタグに無い文字を含むまま転送すると、Tomcatがエンコード済みの"/"を含むパスを拒否し、
            // サイトのデザインが無い素の400画面になる。
            return Tags.looksLikeTag(trimmed)
                    ? new PlayerSearchResult.Found(Tags.normalize(trimmed))
                    : new PlayerSearchResult.NotFound(trimmed);
        }
        if (Tags.usesOnlyTagCharacters(trimmed)) {
            try {
                return new PlayerSearchResult.Found(findPlayer(trimmed).tag());
            } catch (ResourceNotFoundException e) {
                // 名前として探し直す。
            }
        }
        int offset = (Math.max(page, 1) - 1) * SEARCH_PAGE_SIZE;
        PlayerNameSearch result = nameIndex.search(trimmed, offset, SEARCH_PAGE_SIZE);
        if (offset > 0 && result.exact().isEmpty()) {
            int lastPageOffset = Math.max(result.exactTotal() - 1, 0) / SEARCH_PAGE_SIZE * SEARCH_PAGE_SIZE;
            result = nameIndex.search(trimmed, lastPageOffset, SEARCH_PAGE_SIZE);
        }
        if (result.isEmpty()) {
            return new PlayerSearchResult.NotFound(trimmed);
        }
        return new PlayerSearchResult.Candidates(result);
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
