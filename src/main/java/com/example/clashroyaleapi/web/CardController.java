package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.service.CardService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Locale;

@Controller
public class CardController {

    private final CardService cardService;
    private final ViewMapper viewMapper;

    public CardController(CardService cardService, ViewMapper viewMapper) {
        this.cardService = cardService;
        this.viewMapper = viewMapper;
    }

    @GetMapping("/card/{id}")
    public String card(@PathVariable int id, @RequestParam(required = false) String player,
            @RequestParam(required = false) String battle, Model model, Locale locale) {
        model.addAttribute("card", viewMapper.toCardDetail(cardService.byId(id), locale));
        model.addAttribute("origin", CardOrigin.fromQuery(player, battle));
        return "card";
    }
}
