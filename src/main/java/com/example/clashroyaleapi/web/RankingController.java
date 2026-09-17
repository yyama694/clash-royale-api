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
public class RankingController {

    // トップページ(上位100人)より多く見せる画面。APIは最大1000件まで返すが、低スペックVMでの描画コストを考えて200人にしている。
    private static final int PLAYER_RANKING_SIZE = 200;

    private final RankingService rankingService;
    private final RankingScope rankingScope;
    private final ViewMapper viewMapper;

    public RankingController(RankingService rankingService, RankingScope rankingScope, ViewMapper viewMapper) {
        this.rankingService = rankingService;
        this.rankingScope = rankingScope;
        this.viewMapper = viewMapper;
    }

    @GetMapping("/ranking")
    public String ranking(@RequestParam(required = false) String country, HttpServletRequest request,
            HttpServletResponse response, Model model, Locale locale) {
        Optional<Country> selected = rankingScope.resolve(country, request, response, model, locale);

        // タブ切り替えはブラウザ側で行うため、両方のランキングをここで取得しておく(切り替え時に再通信しない)。
        model.addAttribute("globalRanking",
                viewMapper.toClanRankingRows(rankingService.topClans(RankingService.GLOBAL_LOCATION_ID), locale));
        model.addAttribute("localRanking", selected
                .map(c -> viewMapper.toClanRankingRows(rankingService.topClans(c.locationId()), locale))
                .orElseGet(List::of));
        return "ranking";
    }

    @GetMapping("/ranking/players")
    public String playerRanking(@RequestParam(required = false) String country, HttpServletRequest request,
            HttpServletResponse response, Model model, Locale locale) {
        Optional<Country> selected = rankingScope.resolve(country, request, response, model, locale);

        model.addAttribute("globalRanking", viewMapper.toPlayerRankingRows(
                rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID, PLAYER_RANKING_SIZE)));
        model.addAttribute("localRanking", selected
                .map(c -> viewMapper.toPlayerRankingRows(rankingService.topPlayers(c.locationId(),
                        PLAYER_RANKING_SIZE)))
                .orElseGet(List::of));
        return "player-ranking";
    }
}
