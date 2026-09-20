package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.domain.FavoriteFetch;
import com.example.clashroyaleapi.domain.Favorites;
import com.example.clashroyaleapi.domain.GameText;
import com.example.clashroyaleapi.service.ClanService;
import com.example.clashroyaleapi.service.FavoriteService;
import com.example.clashroyaleapi.service.PlayerService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * お気に入り画面の表示と、登録・解除。プレイヤー情報画面・クラン情報画面の☆ボタン、およびお気に入り画面自身の「外す」ボタンから呼ばれる。
 * 登録する名前はフォームから受け取らず、公式APIから取り直す(タグの実在確認も兼ねる。直前に情報画面を開いているのでほぼ確実にキャッシュに当たる)。
 * JSからのfetch(Accept: application/json)にはJSONを返し、画面遷移させずにボタンだけ切り替える。
 * JSが動かないとき、およびお気に入り画面の「外す」ボタンは通常のフォーム送信として受け、303でリダイレクトする
 * (戻り先はRefererではなくタグ・固定のbackパラメータから組み立てるため、任意のURLへ飛ばすオープンリダイレクトにはならない)。
 */
@Controller
@RequestMapping("/favorites")
public class FavoriteController {

    private final PlayerService playerService;
    private final ClanService clanService;
    private final FavoriteService favoriteService;
    private final FavoriteCookies favoriteCookies;
    private final ViewMapper viewMapper;

    public FavoriteController(PlayerService playerService, ClanService clanService, FavoriteService favoriteService,
            FavoriteCookies favoriteCookies, ViewMapper viewMapper) {
        this.playerService = playerService;
        this.clanService = clanService;
        this.favoriteService = favoriteService;
        this.favoriteCookies = favoriteCookies;
        this.viewMapper = viewMapper;
    }

    @GetMapping
    public String favorites(Model model, HttpServletRequest request, HttpServletResponse response) {
        Favorites players = favoriteCookies.read(request, FavoriteKind.PLAYER);
        Favorites clans = favoriteCookies.read(request, FavoriteKind.CLAN);

        List<FavoriteFetch<PlayerResponse>> playerResults = favoriteService.fetchPlayers(players.entries());
        List<FavoriteFetch<ClanResponse>> clanResults = favoriteService.fetchClans(clans.entries());
        // 取得できた最新の名前でCookieを書き直しておく(トップページがAPIを呼ばずにほぼ最新の名前を出せるように)。
        refreshNames(players, playerResults, PlayerResponse::name, FavoriteKind.PLAYER, response);
        refreshNames(clans, clanResults, ClanResponse::name, FavoriteKind.CLAN, response);

        model.addAttribute("favoritePlayers", viewMapper.toFavoritePlayerRows(playerResults));
        model.addAttribute("favoriteClans", viewMapper.toFavoriteClanRows(clanResults));
        return "favorites";
    }

    @PostMapping("/players/{tag}")
    public ResponseEntity<?> addPlayer(@PathVariable String tag, HttpServletRequest request,
            HttpServletResponse response) {
        PlayerResponse player = playerService.findPlayer(tag);
        String pathTag = Tags.toPathSegment(player.tag());
        return add(FavoriteKind.PLAYER, pathTag, GameText.stripFormatting(player.name()), "/player/" + pathTag,
                request, response);
    }

    @PostMapping("/players/{tag}/remove")
    public ResponseEntity<?> removePlayer(@PathVariable String tag, @RequestParam(required = false) String back,
            HttpServletRequest request, HttpServletResponse response) {
        String pathTag = Tags.toPathSegment(tag);
        return remove(FavoriteKind.PLAYER, pathTag, backTo(back, "/player/" + pathTag), request, response);
    }

    @PostMapping("/clans/{tag}")
    public ResponseEntity<?> addClan(@PathVariable String tag, HttpServletRequest request,
            HttpServletResponse response) {
        ClanResponse clan = clanService.findClan(tag);
        String pathTag = Tags.toPathSegment(clan.tag());
        return add(FavoriteKind.CLAN, pathTag, GameText.stripFormatting(clan.name()), "/clan/" + pathTag,
                request, response);
    }

    @PostMapping("/clans/{tag}/remove")
    public ResponseEntity<?> removeClan(@PathVariable String tag, @RequestParam(required = false) String back,
            HttpServletRequest request, HttpServletResponse response) {
        String pathTag = Tags.toPathSegment(tag);
        return remove(FavoriteKind.CLAN, pathTag, backTo(back, "/clan/" + pathTag), request, response);
    }

    private String backTo(String back, String defaultTo) {
        return "favorites".equals(back) ? "/favorites" : defaultTo;
    }

    private ResponseEntity<?> add(FavoriteKind kind, String tag, String name, String backTo,
            HttpServletRequest request, HttpServletResponse response) {
        Favorites favorites = favoriteCookies.read(request, kind);
        if (favorites.isFull() && !favorites.contains(tag)) {
            if (wantsJson(request)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "limit"));
            }
            return redirect(backTo + "?favoriteLimit");
        }
        favoriteCookies.write(response, kind, favorites.add(tag, name));
        return result(request, backTo, true);
    }

    private ResponseEntity<?> remove(FavoriteKind kind, String tag, String backTo,
            HttpServletRequest request, HttpServletResponse response) {
        Favorites favorites = favoriteCookies.read(request, kind);
        favoriteCookies.write(response, kind, favorites.remove(tag));
        return result(request, backTo, false);
    }

    private ResponseEntity<?> result(HttpServletRequest request, String backTo, boolean favorite) {
        if (wantsJson(request)) {
            return ResponseEntity.ok(Map.of("favorite", favorite));
        }
        return redirect(backTo);
    }

    private ResponseEntity<?> redirect(String location) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(location)).build();
    }

    private boolean wantsJson(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains("application/json");
    }

    private <T> void refreshNames(Favorites original, List<FavoriteFetch<T>> results, Function<T, String> nameOf,
            FavoriteKind kind, HttpServletResponse response) {
        Favorites updated = original;
        for (FavoriteFetch<T> result : results) {
            if (result instanceof FavoriteFetch.Found<T> found) {
                updated = updated.updateName(found.tag(), GameText.stripFormatting(nameOf.apply(found.value())));
            }
        }
        if (updated != original) {
            favoriteCookies.write(response, kind, updated);
        }
    }
}
