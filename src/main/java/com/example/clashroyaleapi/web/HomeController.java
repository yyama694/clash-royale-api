package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.client.exception.ClashRoyaleApiException;
import com.example.clashroyaleapi.domain.Country;
import com.example.clashroyaleapi.domain.GameText;
import com.example.clashroyaleapi.service.PlayerService;
import com.example.clashroyaleapi.service.RankingService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Locale;
import java.util.Optional;

@Controller
public class HomeController {

    // 2026-09-20にユーザー判断で100人に変更(以前は「トップページを軽く保つ」方針で10人にしていた)。
    private static final int PLAYER_RANKING_SIZE = 100;

    private final RankingService rankingService;
    private final RankingScope rankingScope;
    private final ViewMapper viewMapper;
    private final FavoriteCookies favoriteCookies;
    private final MeCookie meCookie;
    private final PlayerService playerService;

    public HomeController(RankingService rankingService, RankingScope rankingScope, ViewMapper viewMapper,
            FavoriteCookies favoriteCookies, MeCookie meCookie, PlayerService playerService) {
        this.rankingService = rankingService;
        this.rankingScope = rankingScope;
        this.viewMapper = viewMapper;
        this.favoriteCookies = favoriteCookies;
        this.meCookie = meCookie;
        this.playerService = playerService;
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false) String country, HttpServletRequest request,
            HttpServletResponse response, Model model, Locale locale) {
        Optional<Country> selected = rankingScope.resolve(country, request, response, model, locale).country();

        model.addAttribute("globalRanking", viewMapper.toPlayerRanking(
                rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID, PLAYER_RANKING_SIZE), locale));
        // 国が決まらないときは国別のタブ自体を描かない。
        model.addAttribute("localRanking", selected
                .map(c -> viewMapper.toPlayerRanking(rankingService.topPlayers(c.locationId(),
                        PLAYER_RANKING_SIZE), locale))
                .orElse(null));
        // 注記の「上位n人」を文言に直書きすると定数を変えたときにずれるため、件数も渡す。
        model.addAttribute("playerRankingSize", PLAYER_RANKING_SIZE);
        // トップページでは、訪問者に身近な自国のランキングを最初に見せる(国が決まらないときはグローバル)。
        model.addAttribute("localTabActive", selected.isPresent());
        // お気に入りは名前だけCookieから出す(表示速度を落とさないよう公式APIは呼ばない)。
        model.addAttribute("favoritePlayers", favoriteCookies.read(request, FavoriteKind.PLAYER).entries());
        model.addAttribute("favoriteClans", favoriteCookies.read(request, FavoriteKind.CLAN).entries());
        meCookie.read(request).ifPresent(tag -> addMe(tag, model));
        return "index";
    }

    /**
     * 「これは自分」で登録した人の今の成績。トップページの一番上に出し、再訪したときに最初に目に入るようにする。
     * 公式APIを1回呼ぶ(プレイヤー情報画面と同じキャッシュ)。取れなくてもトップページの他の部分は出す。
     */
    private void addMe(String tag, Model model) {
        model.addAttribute("meTag", tag);
        try {
            PlayerResponse player = playerService.findPlayer(tag);
            model.addAttribute("me", player);
            model.addAttribute("meName", GameText.stripFormatting(player.name()));
            model.addAttribute("meProgress", viewMapper.toPlayerProgress(player));
        } catch (ClashRoyaleApiException e) {
            model.addAttribute("meErrorKey", e.messageKey());
        }
    }
}
