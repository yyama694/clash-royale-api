package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.domain.BalanceUpdate;
import com.example.clashroyaleapi.service.BalanceService;
import com.example.clashroyaleapi.service.CardService;
import com.example.clashroyaleapi.web.view.BalanceUpdateView;

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
import java.util.Map;

/**
 * バランス調整の一覧と、調整ごとの画面。内容は公式のリリースノートを写したもので、対象カードの使用率の推移を添える。
 * 調整ごとの画面は、「クラロワ バランス調整 10月」のような日付つきの検索の入口にする(タイトル・説明文をその調整のものにできる)。
 */
@Controller
public class BalanceController {

    private final BalanceService balanceService;
    private final CardService cardService;
    private final ViewMapper viewMapper;
    private final PageSummaries pageSummaries;

    public BalanceController(BalanceService balanceService, CardService cardService, ViewMapper viewMapper,
            PageSummaries pageSummaries) {
        this.balanceService = balanceService;
        this.cardService = cardService;
        this.viewMapper = viewMapper;
        this.pageSummaries = pageSummaries;
    }

    @GetMapping("/balance")
    public String balance(Model model, Locale locale) {
        model.addAttribute("updates", views(balanceService.updates(), locale));
        model.addAttribute("pageTitle", pageSummaries.balanceTitle(locale));
        model.addAttribute("pageDescription", pageSummaries.balanceSummary(balanceService.updates(), locale));
        return "balance";
    }

    @GetMapping("/balance/{date}")
    public String update(@PathVariable String date, Model model, Locale locale) {
        BalanceUpdate update = balanceService.update(parse(date))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("updates", views(List.of(update), locale));
        model.addAttribute("single", true);
        model.addAttribute("pageTitle", pageSummaries.balanceUpdateTitle(update, locale));
        model.addAttribute("pageDescription", pageSummaries.balanceSummary(List.of(update), locale));
        return "balance";
    }

    private List<BalanceUpdateView> views(List<BalanceUpdate> updates, Locale locale) {
        Map<String, CardsResponse.Card> cardsByName = cardService.cardsByName();
        return updates.stream()
                .map(update -> viewMapper.toBalanceUpdate(update, balanceService.usage(update), cardsByName, locale))
                .toList();
    }

    private static LocalDate parse(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
