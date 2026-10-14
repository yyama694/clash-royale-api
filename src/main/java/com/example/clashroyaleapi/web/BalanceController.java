package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.domain.CardUsage;
import com.example.clashroyaleapi.service.BalanceService;
import com.example.clashroyaleapi.service.CardService;
import com.example.clashroyaleapi.service.CardUsageService;
import com.example.clashroyaleapi.web.view.BalanceUpdateView;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** バランス調整の一覧。内容は公式のリリースノートを写したもので、対象カードの調整前と今の使用率を添える。 */
@Controller
public class BalanceController {

    private final BalanceService balanceService;
    private final CardService cardService;
    private final CardUsageService cardUsageService;
    private final ViewMapper viewMapper;
    private final PageSummaries pageSummaries;

    public BalanceController(BalanceService balanceService, CardService cardService, CardUsageService cardUsageService,
            ViewMapper viewMapper, PageSummaries pageSummaries) {
        this.balanceService = balanceService;
        this.cardService = cardService;
        this.cardUsageService = cardUsageService;
        this.viewMapper = viewMapper;
        this.pageSummaries = pageSummaries;
    }

    @GetMapping("/balance")
    public String balance(Model model, Locale locale) {
        Map<String, CardsResponse.Card> cardsByName = cardService.cardsByName();
        CardUsage current = cardUsageService.current().orElse(null);
        List<BalanceUpdateView> updates = balanceService.updates().stream()
                .map(update -> viewMapper.toBalanceUpdate(update, balanceService.baseline(update).orElse(null),
                        current, cardsByName, locale))
                .toList();
        model.addAttribute("updates", updates);
        model.addAttribute("pageTitle", pageSummaries.balanceTitle(locale));
        model.addAttribute("pageDescription", pageSummaries.balanceSummary(balanceService.updates(), locale));
        return "balance";
    }
}
