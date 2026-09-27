package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.service.CardService;
import com.example.clashroyaleapi.service.TopPlayerDeckService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Locale;
import java.util.Map;

@Controller
public class TopPlayerDeckController {

    private final TopPlayerDeckService topPlayerDeckService;
    private final CardService cardService;
    private final ViewMapper viewMapper;
    private final TimeFormatter timeFormatter;

    public TopPlayerDeckController(TopPlayerDeckService topPlayerDeckService, CardService cardService,
            ViewMapper viewMapper, TimeFormatter timeFormatter) {
        this.topPlayerDeckService = topPlayerDeckService;
        this.cardService = cardService;
        this.viewMapper = viewMapper;
        this.timeFormatter = timeFormatter;
    }

    @GetMapping("/decks")
    public String decks(@RequestParam(required = false) Integer card,
            @RequestParam(required = false, defaultValue = "1") int page, Model model, Locale locale) {
        Map<Integer, CardsResponse.Card> cardsById = cardService.allById();
        // 一覧に無いカードIDは、絞り込まずに全員を出す(0件の画面を出さないため)。
        Integer selected = card != null && cardsById.containsKey(card) ? card : null;
        model.addAttribute("cardOptions", viewMapper.toCardOptions(cardsById.values(), locale));
        model.addAttribute("selectedCard", selected);
        // 初回の集計が終わるまで(デプロイ直後の1時間ほど)は、一覧の代わりに案内文を出す。
        topPlayerDeckService.page(selected, page).ifPresent(result -> {
            model.addAttribute("decks", result.decks().stream()
                    .map(deck -> viewMapper.toTopPlayerDeck(deck, cardsById, locale))
                    .toList());
            model.addAttribute("collectedAt", timeFormatter.instant(result.collectedAt(), locale));
            model.addAttribute("total", result.total());
            model.addAttribute("from", result.from());
            model.addAttribute("to", result.from() == 0 ? 0 : result.from() + result.decks().size() - 1);
            model.addAttribute("prevPage", result.page() > 1 ? result.page() - 1 : null);
            model.addAttribute("nextPage", result.hasNext() ? result.page() + 1 : null);
        });
        return "decks";
    }
}
