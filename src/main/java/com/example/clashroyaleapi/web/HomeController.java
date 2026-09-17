package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.service.RankingService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    // トップページは概要だけを見せ、続きは個人ランキング画面に任せる。
    private static final int SUMMARY_SIZE = 3;

    private final RankingService rankingService;
    private final ViewMapper viewMapper;

    public HomeController(RankingService rankingService, ViewMapper viewMapper) {
        this.rankingService = rankingService;
        this.viewMapper = viewMapper;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("playerRanking", viewMapper.toPlayerRankingRows(
                rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID, SUMMARY_SIZE)));
        return "index";
    }
}
