package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.domain.RankingScope;
import com.example.clashroyaleapi.service.RankingService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final RankingService rankingService;
    private final ViewMapper viewMapper;

    public HomeController(RankingService rankingService, ViewMapper viewMapper) {
        this.rankingService = rankingService;
        this.viewMapper = viewMapper;
    }

    @GetMapping("/")
    public String index(Model model) {
        // タブ切り替えはブラウザ側で行うため、両方のランキングをここで取得しておく(切り替え時に再通信しない)。
        model.addAttribute("globalRanking",
                viewMapper.toClanRankingRows(rankingService.topClans(RankingScope.GLOBAL)));
        model.addAttribute("localRanking",
                viewMapper.toClanRankingRows(rankingService.topClans(RankingScope.LOCAL)));
        return "index";
    }
}
