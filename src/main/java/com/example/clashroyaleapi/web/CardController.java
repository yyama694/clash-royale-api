package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.service.CardService;
import com.example.clashroyaleapi.service.CardUsageService;
import com.example.clashroyaleapi.web.view.CardDetailView;
import com.example.clashroyaleapi.web.view.CardUsageView;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Locale;

@Controller
public class CardController {

    private final CardService cardService;
    private final CardUsageService cardUsageService;
    private final ViewMapper viewMapper;
    private final PageSummaries pageSummaries;

    public CardController(CardService cardService, CardUsageService cardUsageService, ViewMapper viewMapper,
            PageSummaries pageSummaries) {
        this.cardService = cardService;
        this.cardUsageService = cardUsageService;
        this.viewMapper = viewMapper;
        this.pageSummaries = pageSummaries;
    }

    @GetMapping("/cards")
    public String cards(Model model, Locale locale) {
        model.addAttribute("groups", viewMapper.toCardCatalog(cardService.catalog(), locale));
        model.addAttribute("elixirCosts", cardService.elixirCosts());
        return "cards";
    }

    @GetMapping("/card/{id}")
    public String card(@PathVariable int id, Model model, Locale locale) {
        CardDetailView card = viewMapper.toCardDetail(cardService.byId(id), locale);
        model.addAttribute("card", card);
        model.addAttribute("pageTitle", pageSummaries.cardTitle(card.name(), locale));
        // 初回の集計が終わるまで(デプロイ直後の1時間ほど)は、使用率の欄ごと出さない。説明文もサイト共通のものになる。
        cardUsageService.current().ifPresent(usage -> {
            CardUsageView usageView = viewMapper.toCardUsage(usage, id, cardService.allById(), locale);
            model.addAttribute("usage", usageView);
            model.addAttribute("pageDescription", pageSummaries.cardSummary(card.name(), usageView, locale));
        });
        return "card";
    }
}
