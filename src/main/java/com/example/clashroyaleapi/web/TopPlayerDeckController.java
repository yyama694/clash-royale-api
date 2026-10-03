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
import java.util.Optional;

@Controller
public class TopPlayerDeckController {

    private final TopPlayerDeckService topPlayerDeckService;
    private final CardService cardService;
    private final ViewMapper viewMapper;
    private final TimeFormatter timeFormatter;
    private final PageSummaries pageSummaries;

    public TopPlayerDeckController(TopPlayerDeckService topPlayerDeckService, CardService cardService,
            ViewMapper viewMapper, TimeFormatter timeFormatter, PageSummaries pageSummaries) {
        this.topPlayerDeckService = topPlayerDeckService;
        this.cardService = cardService;
        this.viewMapper = viewMapper;
        this.timeFormatter = timeFormatter;
        this.pageSummaries = pageSummaries;
    }

    @GetMapping("/decks")
    public String decks(@RequestParam(required = false) Integer card,
            @RequestParam(required = false, defaultValue = "1") int page, Model model, Locale locale) {
        Map<Integer, CardsResponse.Card> cardsById = cardService.allById();
        // 一覧に無いカードIDは、絞り込まずに全員を出す(0件の画面を出さないため)。
        Integer selected = card != null && cardsById.containsKey(card) ? card : null;
        String cardName = selected == null ? null : viewMapper.cardName(cardsById.get(selected), locale);
        model.addAttribute("cardOptions", viewMapper.toCardOptions(cardsById.values(), locale));
        model.addAttribute("selectedCard", selected);

        // 初回の集計が終わるまで(デプロイ直後の1時間ほど)は空で、一覧の代わりに案内文を出す。
        Optional<TopPlayerDeckService.Page> found = topPlayerDeckService.page(selected, page);
        TopPlayerDeckService.Page pageOrNull = found.orElse(null);
        model.addAttribute("heading", pageSummaries.decksHeading(cardName, locale));
        model.addAttribute("pageTitle", pageSummaries.decksTitle(cardName, pageOrNull, locale));
        model.addAttribute("pageDescription", pageSummaries.decksSummary(cardName, pageOrNull, locale));
        // トップ層で誰も使っていないカードの画面は中身が空なので、検索結果に出さない(sitemapからも外している)。
        model.addAttribute("noindex", cardName != null && pageOrNull != null && pageOrNull.total() == 0);
        found.ifPresent(result -> {
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
