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

    // 検索がトップページの主役なので、ランキングは概要に留めて続きは個人ランキング画面に任せる。
    // 100人表示にしていた時期は、ページの高さがPCで約5,600px・スマホで約12,500pxになり、下のクランランキングへの導線が埋もれた。
    private static final int PLAYER_RANKING_SIZE = 10;

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
        Optional<Country> selected = rankingScope.resolve(country, request, response, model, locale);

        model.addAttribute("globalRanking", viewMapper.toPlayerRankingRows(
                rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID, PLAYER_RANKING_SIZE)));
        model.addAttribute("localRanking", selected
                .map(c -> viewMapper.toPlayerRankingRows(rankingService.topPlayers(c.locationId(),
                        PLAYER_RANKING_SIZE)))
                .orElseGet(List::of));
        // トップページでは、訪問者に身近な自国のランキングを最初に見せる(国が決まらないときはグローバル)。
        model.addAttribute("localTabActive", selected.isPresent());
        return "index";
    }
}
