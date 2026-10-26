package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.service.FavoriteService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * プレイヤー情報画面の「これは自分」の登録・解除。画面のJSからのfetch(Accept: application/json)にはJSONを返し、
 * フォームの送信(JSが動かない環境)には元のプレイヤー情報画面へ303で戻す
 * (戻り先はタグから組み立てるので、任意のURLへ飛ばすオープンリダイレクトにはならない)。
 * 他サイトからの登録・解除(CSRF)は CrossSiteRequestGuard がここに届く前に拒否する。
 */
@Controller
@RequestMapping("/me")
public class MeController {

    private final PlayerService playerService;
    private final FavoriteService favoriteService;
    private final MeCookie meCookie;

    public MeController(PlayerService playerService, FavoriteService favoriteService, MeCookie meCookie) {
        this.playerService = playerService;
        this.favoriteService = favoriteService;
        this.meCookie = meCookie;
    }

    /** タグは公式APIで実在を確かめてから保存する(直前にプレイヤー情報画面を開いているので、ほぼキャッシュに当たる)。 */
    @PostMapping("/{tag}")
    public ResponseEntity<?> set(@PathVariable String tag, @RequestParam(required = false) String lang,
            HttpServletRequest request, HttpServletResponse response) {
        String pathTag = Tags.toPathSegment(playerService.findPlayer(tag).tag());
        meCookie.write(response, pathTag);
        return result(request, pathTag, lang, true);
    }

    @PostMapping("/{tag}/remove")
    public ResponseEntity<?> remove(@PathVariable String tag, @RequestParam(required = false) String lang,
            HttpServletRequest request, HttpServletResponse response) {
        String pathTag = favoriteService.tagToRemove(tag);
        meCookie.clear(response);
        return result(request, pathTag, lang, false);
    }

    private static ResponseEntity<?> result(HttpServletRequest request, String pathTag, String lang, boolean me) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept != null && accept.contains("application/json")) {
            return ResponseEntity.ok(Map.of("me", me));
        }
        return redirectToPlayer(pathTag, lang);
    }

    // 押した画面の言語(?lang=)で戻す。検索結果などから ?lang= 付きで来た人が、押したとたん別の言語にならないように。
    private static ResponseEntity<Void> redirectToPlayer(String pathTag, String lang) {
        String path = "/player/" + UriUtils.encodePathSegment(pathTag, StandardCharsets.UTF_8)
                + SupportedLanguages.fromParameter(lang)
                        .map(locale -> "?" + WebConstants.LANGUAGE_PARAM + "=" + locale.getLanguage())
                        .orElse("");
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(path)).build();
    }
}
