package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.service.RankingHistoryService;
import com.example.clashroyaleapi.service.RankingService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Controller
public class RankingMovementController {

    /** 最新の動きの画面から直接たどれる日の数。それより前は日ごとの画面の「前の日」とsitemapからたどる。 */
    private static final int RECENT_DAYS = 14;

    private final RankingHistoryService historyService;
    private final ViewMapper viewMapper;
    private final PageSummaries pageSummaries;

    public RankingMovementController(RankingHistoryService historyService, ViewMapper viewMapper,
            PageSummaries pageSummaries) {
        this.historyService = historyService;
        this.viewMapper = viewMapper;
        this.pageSummaries = pageSummaries;
    }

    @GetMapping("/ranking/players/movements")
    public String latest(Model model, Locale locale) {
        List<LocalDate> days = historyService.archiveDays();
        fill(model, historyService.latest(), null, locale);
        model.addAttribute("recentDays", viewMapper.toDayLinks(days.stream().limit(RECENT_DAYS).toList(), locale));
        return "ranking-movements";
    }

    @GetMapping("/ranking/players/movements/{date}")
    public String day(@PathVariable String date, Model model, Locale locale) {
        LocalDate day = parse(date);
        Optional<RankingHistoryService.Report> report = historyService.day(day);
        if (report.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        List<LocalDate> days = historyService.archiveDays();
        fill(model, report, day, locale);
        model.addAttribute("previousDay", days.stream().filter(other -> other.isBefore(day)).findFirst().orElse(null));
        model.addAttribute("nextDay", days.stream().filter(other -> other.isAfter(day)).reduce((a, b) -> b)
                .orElse(null));
        return "ranking-movements";
    }

    private void fill(Model model, Optional<RankingHistoryService.Report> report, LocalDate day, Locale locale) {
        RankingHistoryService.Report reportOrNull = report.orElse(null);
        model.addAttribute("day", day);
        model.addAttribute("heading", pageSummaries.movementsHeading(day, locale));
        model.addAttribute("pageTitle", pageSummaries.movementsTitle(reportOrNull, day, locale));
        model.addAttribute("pageDescription", pageSummaries.movementsSummary(reportOrNull, locale));
        model.addAttribute("report", report.map(found -> viewMapper.toRankingReport(found, locale)).orElse(null));
        model.addAttribute("stale", report.map(historyService::isStale).orElse(false));
        model.addAttribute("rankingSize", RankingService.MAX_PLAYER_RANKING_SIZE);
        model.addAttribute("topSize", RankingHistoryService.TOP_SIZE);
    }

    private static LocalDate parse(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
