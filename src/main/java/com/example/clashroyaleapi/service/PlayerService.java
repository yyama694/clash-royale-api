package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.client.exception.BattleNotFoundException;
import com.example.clashroyaleapi.client.exception.ClashRoyaleApiException;
import com.example.clashroyaleapi.client.exception.ResourceNotFoundException;
import com.example.clashroyaleapi.config.PlayerIndexProperties;
import com.example.clashroyaleapi.domain.BattleResult;
import com.example.clashroyaleapi.domain.CurrentDeck;
import com.example.clashroyaleapi.domain.PlayerBattleStats;
import com.example.clashroyaleapi.domain.PlayerNameMatch;
import com.example.clashroyaleapi.domain.PlayerNameSearch;
import com.example.clashroyaleapi.domain.PlayerSearchResult;
import com.example.clashroyaleapi.domain.PlayerSighting;
import com.example.clashroyaleapi.store.PlayerNameIndex;
import com.example.clashroyaleapi.store.PlayerSightingLog;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;

@Service
public class PlayerService {

    // 名前検索の1ページの人数。よくある名前は完全一致だけで数百人以上になるため、ページ送りにする。
    public static final int SEARCH_PAGE_SIZE = 50;
    // 索引側で offset + ページの人数を計算しても溢れない値。
    private static final int MAX_OFFSET = Integer.MAX_VALUE - SEARCH_PAGE_SIZE;

    // 同名の人を見分けるため、今のトロフィー・クランなどを添える候補の数。名前検索は1日に十数回(2026-10)と少ないが、
    // 1回の検索で公式APIを呼ぶ数がこの人数まで増えるので絞る。同時に呼ぶ数はお気に入り画面と同じ4本にする。
    public static final int CANDIDATE_DETAILS_LIMIT = 20;
    private static final int MAX_CONCURRENT = 4;

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
        // ゲームの共有リンクと全角で打ったタグもタグとして読む。名前として探すときは入力のまま使う。
        String tagInput = Tags.playerTagInput(trimmed);
        if (!nameSearchEnabled || tagInput.startsWith("#")) {
            // "/"などタグに無い文字を含むまま転送すると、Tomcatがエンコード済みの"/"を含むパスを拒否し、
            // サイトのデザインが無い素の400画面になる。
            return Tags.looksLikeTag(tagInput)
                    ? new PlayerSearchResult.Found(Tags.normalize(tagInput))
                    : new PlayerSearchResult.NotFound(trimmed);
        }
        if (Tags.usesOnlyTagCharacters(tagInput)) {
            try {
                return new PlayerSearchResult.Found(findPlayer(tagInput).tag());
            } catch (ResourceNotFoundException e) {
                // 名前として探し直す。
            }
        }
        // URLで大きなページ番号を渡されても int が溢れないよう上限をかける(範囲外は下で最後のページに直す)。
        int offset = (int) Math.min((Math.max(page, 1) - 1L) * SEARCH_PAGE_SIZE, MAX_OFFSET);
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

    /**
     * 名前検索の候補が2人以上のとき、先頭の{@value CANDIDATE_DETAILS_LIMIT}人(名前が一致した人、前方一致の人の順)の
     * 今のプロフィール。キーは候補のタグ。取れなかった人は入れない(画面はその人だけ名前とタグで出す)。
     * 候補が1人なら見分ける必要が無いので取らない。
     */
    public Map<String, PlayerResponse> candidateDetails(PlayerNameSearch result) {
        List<String> tags = Stream.concat(result.exact().stream(), result.prefix().stream())
                .map(PlayerNameMatch::tag)
                .toList();
        if (tags.size() < 2) {
            return Map.of();
        }
        Semaphore semaphore = new Semaphore(MAX_CONCURRENT);
        List<Future<Optional<PlayerResponse>>> futures;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            futures = tags.stream().limit(CANDIDATE_DETAILS_LIMIT)
                    .map(tag -> executor.submit(() -> lookup(tag, semaphore)))
                    .toList();
        }
        Map<String, PlayerResponse> details = new HashMap<>();
        for (int i = 0; i < futures.size(); i++) {
            String tag = tags.get(i);
            join(futures.get(i)).ifPresent(player -> details.put(tag, player));
        }
        return details;
    }

    // 見分けるための補足なので、1人取れなくても検索結果は出す。
    private Optional<PlayerResponse> lookup(String tag, Semaphore semaphore) throws InterruptedException {
        semaphore.acquire();
        try {
            return Optional.of(findPlayer(tag));
        } catch (ClashRoyaleApiException e) {
            return Optional.empty();
        } finally {
            semaphore.release();
        }
    }

    private static <T> T join(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
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

    /** @param battleLog 取得できなかったときは空。その場合、公式APIが返さなかったカードは補わない */
    public Optional<CurrentDeck> currentDeckOf(PlayerResponse player, List<BattleLogEntry> battleLog) {
        return CurrentDeck.of(player, battleLog);
    }

    /**
     * battlelogは新しい対戦が入るたびに先頭へ積まれるため、位置(index)で参照すると
     * 画面を開き直した際に黙って別の対戦が表示されてしまう。battleTimeをキーにして特定する。
     * 片側が欠けた対戦は対戦履歴の一覧にも出さないため、URLで直接指定されても見つからない扱いにする。
     */
    public BattleLogEntry findBattle(String tag, String battleTime) {
        Optional<BattleLogEntry> battle = findBattleLog(tag).stream()
                .filter(entry -> battleTime.equals(entry.battleTime()))
                .filter(BattleResult::hasBothSides)
                .findFirst();
        return battle.orElseThrow(() -> new BattleNotFoundException("battle " + battleTime + " of " + tag));
    }
}
