package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.service.CardService;
import com.example.clashroyaleapi.service.CardUsageService;
import com.example.clashroyaleapi.service.TopPlayerDeckService;
import com.example.clashroyaleapi.web.view.CardDetailView;
import com.example.clashroyaleapi.web.view.CardUsageView;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Locale;
import java.util.Map;

@Controller
public class CardController {

    /** カード詳細画面に出すトッププレイヤーのデッキの数。人気のカードは数百人が使っていて、全員だと画面が長くなりすぎる。 */
    static final int TOP_DECKS_ON_CARD_PAGE = 5;

    private final CardService cardService;
    private final CardUsageService cardUsageService;
    private final TopPlayerDeckService topPlayerDeckService;
    private final ViewMapper viewMapper;
    private final PageSummaries pageSummaries;

    public CardController(CardService cardService, CardUsageService cardUsageService,
            TopPlayerDeckService topPlayerDeckService, ViewMapper viewMapper, PageSummaries pageSummaries) {
        this.cardService = cardService;
        this.cardUsageService = cardUsageService;
        this.topPlayerDeckService = topPlayerDeckService;
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
        Map<Integer, CardsResponse.Card> cardsById = cardService.allById();
        model.addAttribute("card", card);
        model.addAttribute("pageTitle", pageSummaries.cardTitle(card.name(), locale));
        // 初回の集計が終わるまで(デプロイ直後の1時間ほど)は、使用率の欄ごと出さない。説明文もサイト共通のものになる。
        cardUsageService.current().ifPresent(usage -> {
            CardUsageView usageView = viewMapper.toCardUsage(usage, id, cardsById, locale);
            model.addAttribute("usage", usageView);
            model.addAttribute("pageDescription", pageSummaries.cardSummary(card.name(), usageView, locale));
        });
        // 全員分はデッキ画面(カードで絞り込み)にあるので、ここでは順位の高い数人だけを出してそこへつなぐ。
        topPlayerDeckService.page(id, 1, TOP_DECKS_ON_CARD_PAGE).ifPresent(page -> {
            model.addAttribute("topDecks", page.decks().stream()
                    .map(deck -> viewMapper.toTopPlayerDeck(deck, cardsById, locale))
                    .toList());
            model.addAttribute("topDecksTotal", page.total());
        });
        return "card";
    }
}
