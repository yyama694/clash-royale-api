package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.service.CardService;
import com.example.clashroyaleapi.service.CardUsageService;

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

    public CardController(CardService cardService, CardUsageService cardUsageService, ViewMapper viewMapper) {
        this.cardService = cardService;
        this.cardUsageService = cardUsageService;
        this.viewMapper = viewMapper;
    }

    @GetMapping("/cards")
    public String cards(Model model, Locale locale) {
        model.addAttribute("groups", viewMapper.toCardCatalog(cardService.catalog(), locale));
        model.addAttribute("elixirCosts", cardService.elixirCosts());
        return "cards";
    }

    @GetMapping("/card/{id}")
    public String card(@PathVariable int id, Model model, Locale locale) {
        model.addAttribute("card", viewMapper.toCardDetail(cardService.byId(id), locale));
        // 初回の集計が終わるまで(デプロイ直後の1時間ほど)は、使用率の欄ごと出さない。
        cardUsageService.current().ifPresent(usage -> model.addAttribute("usage",
                viewMapper.toCardUsage(usage, id, cardService.allById(), locale)));
        return "card";
    }
}
