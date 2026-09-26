package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.client.exception.ClashRoyaleApiException;
import com.example.clashroyaleapi.client.exception.ResourceNotFoundException;
import com.example.clashroyaleapi.domain.FavoriteFetch;
import com.example.clashroyaleapi.domain.Favorites;
import com.example.clashroyaleapi.domain.GameText;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

/**
 * お気に入りの登録・解除に使うタグと名前の決定と、お気に入り画面用のプレイヤー・クランの取得。
 * 画面用の取得は最大20件(プレイヤー10+クラン10)あり、順番に呼ぶとキャッシュが空のとき数秒かかるため、
 * 仮想スレッドで並行に取得する。同時実行数は公式APIのレート制限の値が未確認なので{@value MAX_CONCURRENT}本に絞る。
 */
@Service
public class FavoriteService {

    private static final int MAX_CONCURRENT = 4;

    private final PlayerService playerService;
    private final ClanService clanService;

    public FavoriteService(PlayerService playerService, ClanService clanService) {
        this.playerService = playerService;
        this.clanService = clanService;
    }

    /**
     * 登録するプレイヤーのタグと名前。名前はフォームから受け取らず公式APIから取り直す
     * (タグの実在確認も兼ねる。直前に情報画面を開いているのでほぼ確実にキャッシュに当たる)。
     */
    public Favorites.Entry playerEntry(String tag) {
        PlayerResponse player = playerService.findPlayer(tag);
        return new Favorites.Entry(Tags.toPathSegment(player.tag()), GameText.stripFormatting(player.name()));
    }

    public Favorites.Entry clanEntry(String tag) {
        ClanResponse clan = clanService.findClan(tag);
        return new Favorites.Entry(Tags.toPathSegment(clan.tag()), GameText.stripFormatting(clan.name()));
    }

    /**
     * 解除するタグ(先頭の"#"なし)。お気に入りに入り得ない形式(Cookieの読み込みでも捨てる)は、見つからないとして扱う。
     * そのまま戻り先のURLに入れると、空白などを含むタグで URI の組み立てに失敗するため。
     */
    public String tagToRemove(String tag) {
        if (!Tags.looksLikeTag(tag)) {
            throw new ResourceNotFoundException("not a tag: " + tag, null);
        }
        return Tags.toPathSegment(tag);
    }

    /** 取得できた最新の名前で保存名を書き直す。どれも変わらなければ同じものを返す。 */
    public Favorites refreshPlayerNames(Favorites favorites, List<FavoriteFetch<PlayerResponse>> results) {
        return refreshNames(favorites, results, PlayerResponse::name);
    }

    public Favorites refreshClanNames(Favorites favorites, List<FavoriteFetch<ClanResponse>> results) {
        return refreshNames(favorites, results, ClanResponse::name);
    }

    private static <T> Favorites refreshNames(Favorites favorites, List<FavoriteFetch<T>> results,
            Function<T, String> nameOf) {
        Favorites updated = favorites;
        for (FavoriteFetch<T> result : results) {
            if (result instanceof FavoriteFetch.Found<T> found) {
                updated = updated.updateName(found.tag(), GameText.stripFormatting(nameOf.apply(found.value())));
            }
        }
        return updated;
    }

    public List<FavoriteFetch<PlayerResponse>> fetchPlayers(List<Favorites.Entry> entries) {
        return fetchAll(entries, entry -> playerService.findPlayer(entry.tag()));
    }

    public List<FavoriteFetch<ClanResponse>> fetchClans(List<Favorites.Entry> entries) {
        return fetchAll(entries, entry -> clanService.findClan(entry.tag()));
    }

    private <T> List<FavoriteFetch<T>> fetchAll(List<Favorites.Entry> entries,
            Function<Favorites.Entry, T> fetch) {
        if (entries.isEmpty()) {
            return List.of();
        }
        Semaphore semaphore = new Semaphore(MAX_CONCURRENT);
        List<Future<FavoriteFetch<T>>> futures;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            futures = entries.stream()
                    .map(entry -> executor.submit(() -> lookup(entry, fetch, semaphore)))
                    .toList();
        }
        return futures.stream().map(FavoriteService::join).toList();
    }

    private <T> FavoriteFetch<T> lookup(Favorites.Entry entry, Function<Favorites.Entry, T> fetch,
            Semaphore semaphore) throws InterruptedException {
        semaphore.acquire();
        try {
            return new FavoriteFetch.Found<>(entry.tag(), entry.name(), fetch.apply(entry));
        } catch (ResourceNotFoundException e) {
            return new FavoriteFetch.NotFound<>(entry.tag(), entry.name());
        } catch (ClashRoyaleApiException e) {
            return new FavoriteFetch.Unavailable<>(entry.tag(), entry.name());
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
}
