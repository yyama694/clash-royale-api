package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.domain.Country;
import com.example.clashroyaleapi.service.RankingService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Controller
public class HomeController {

    // 2026-09-20にユーザー判断で100人に変更(以前は「トップページを軽く保つ」方針で10人にしていた)。
    private static final int PLAYER_RANKING_SIZE = 100;

    private final RankingService rankingService;
    private final RankingScope rankingScope;
    private final ViewMapper viewMapper;

    public HomeController(RankingService rankingService, RankingScope rankingScope, ViewMapper viewMapper) {
        this.rankingService = rankingService;
        this.rankingScope = rankingScope;
        this.viewMapper = viewMapper;
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false) String country, HttpServletRequest request,
            HttpServletResponse response, Model model, Locale locale) {
        Optional<Country> selected = rankingScope.resolve(country, request, response, model, locale).country();

        model.addAttribute("globalRanking", viewMapper.toPlayerRankingRows(
                rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID, PLAYER_RANKING_SIZE)));
        model.addAttribute("localRanking", selected
                .map(c -> viewMapper.toPlayerRankingRows(rankingService.topPlayers(c.locationId(),
                        PLAYER_RANKING_SIZE)))
                .orElseGet(List::of));
        // 注記の「上位n人」を文言に直書きすると定数を変えたときにずれるため、件数も渡す。
        model.addAttribute("playerRankingSize", PLAYER_RANKING_SIZE);
        // トップページでは、訪問者に身近な自国のランキングを最初に見せる(国が決まらないときはグローバル)。
        model.addAttribute("localTabActive", selected.isPresent());
        return "index";
    }
}
