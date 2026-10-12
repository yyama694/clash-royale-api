package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;
import com.example.clashroyaleapi.service.RankingService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * 終わったシーズンごとのランク戦の最終順位。シーズンの終わりは最終順位が話題になり、検索もされるので、
 * シーズンごとに固定のページを置く。順位は公式APIの終わったシーズンのランキング(世界の上位1000人)。
 */
@Controller
public class SeasonRankingController {

    private final RankingService rankingService;
    private final ViewMapper viewMapper;
    private final PageSummaries pageSummaries;

    public SeasonRankingController(RankingService rankingService, ViewMapper viewMapper,
            PageSummaries pageSummaries) {
        this.rankingService = rankingService;
        this.viewMapper = viewMapper;
        this.pageSummaries = pageSummaries;
    }

    @GetMapping("/ranking/players/seasons")
    public String seasons(Model model, Locale locale) {
        model.addAttribute("seasons", viewMapper.toSeasonLinks(rankingService.finishedSeasons(), locale));
        model.addAttribute("rankingSize", RankingService.MAX_PLAYER_RANKING_SIZE);
        return "seasons";
    }

    @GetMapping("/ranking/players/seasons/{season}")
    public String season(@PathVariable String season, Model model, Locale locale) {
        YearMonth month = parse(season);
        List<PlayerRankingResponse.RankedPlayer> players = rankingService.finalStandings(month)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<YearMonth> seasons = rankingService.finishedSeasons();
        model.addAttribute("heading", pageSummaries.seasonHeading(month, locale));
        model.addAttribute("pageTitle", pageSummaries.seasonTitle(month, players, locale));
        model.addAttribute("pageDescription", pageSummaries.seasonSummary(month, players, locale));
        model.addAttribute("lead", pageSummaries.seasonLead(month, players.size(), locale));
        model.addAttribute("ranking", viewMapper.toFinalStandings(players));
        model.addAttribute("previousSeason", seasons.stream().filter(other -> other.isBefore(month)).findFirst()
                .map(other -> viewMapper.toSeasonLink(other, locale)).orElse(null));
        model.addAttribute("nextSeason", seasons.stream().filter(other -> other.isAfter(month)).reduce((a, b) -> b)
                .map(other -> viewMapper.toSeasonLink(other, locale)).orElse(null));
        return "season-ranking";
    }

    private static YearMonth parse(String season) {
        try {
            return YearMonth.parse(season);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
