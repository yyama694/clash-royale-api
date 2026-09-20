package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.domain.Favorites;
import com.example.clashroyaleapi.domain.GameText;
import com.example.clashroyaleapi.service.ClanService;
import com.example.clashroyaleapi.service.PlayerService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.net.URI;
import java.util.Map;

/**
 * お気に入りの登録・解除。プレイヤー情報画面・クラン情報画面の☆ボタンから呼ばれる。
 * 登録する名前はフォームから受け取らず、公式APIから取り直す(タグの実在確認も兼ねる。直前に情報画面を開いているのでほぼ確実にキャッシュに当たる)。
 * JSからのfetch(Accept: application/json)にはJSONを返し、画面遷移させずにボタンだけ切り替える。
 * JSが動かないときは通常のフォーム送信として受け、登録・解除の対象画面へ303でリダイレクトする
 * (戻り先はRefererではなくタグから組み立てるため、任意のURLへ飛ばすオープンリダイレクトにはならない)。
 */
@Controller
@RequestMapping("/favorites")
public class FavoriteController {

    private final PlayerService playerService;
    private final ClanService clanService;
    private final FavoriteCookies favoriteCookies;

    public FavoriteController(PlayerService playerService, ClanService clanService, FavoriteCookies favoriteCookies) {
        this.playerService = playerService;
        this.clanService = clanService;
        this.favoriteCookies = favoriteCookies;
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
    public ResponseEntity<?> removePlayer(@PathVariable String tag, HttpServletRequest request,
            HttpServletResponse response) {
        String pathTag = Tags.toPathSegment(tag);
        return remove(FavoriteKind.PLAYER, pathTag, "/player/" + pathTag, request, response);
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
    public ResponseEntity<?> removeClan(@PathVariable String tag, HttpServletRequest request,
            HttpServletResponse response) {
        String pathTag = Tags.toPathSegment(tag);
        return remove(FavoriteKind.CLAN, pathTag, "/clan/" + pathTag, request, response);
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
}
