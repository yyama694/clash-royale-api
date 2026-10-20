package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.domain.DeckWinRates;
import com.example.clashroyaleapi.service.CardService;
import com.example.clashroyaleapi.service.DeckWinRateService;
import com.example.clashroyaleapi.service.TopPlayerDeckService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Controller
public class TopPlayerDeckController {

    private final TopPlayerDeckService topPlayerDeckService;
    private final DeckWinRateService deckWinRateService;
    private final CardService cardService;
    private final ViewMapper viewMapper;
    private final TimeFormatter timeFormatter;
    private final PageSummaries pageSummaries;

    public TopPlayerDeckController(TopPlayerDeckService topPlayerDeckService, DeckWinRateService deckWinRateService,
            CardService cardService, ViewMapper viewMapper, TimeFormatter timeFormatter, PageSummaries pageSummaries) {
        this.topPlayerDeckService = topPlayerDeckService;
        this.deckWinRateService = deckWinRateService;
        this.cardService = cardService;
        this.viewMapper = viewMapper;
        this.timeFormatter = timeFormatter;
        this.pageSummaries = pageSummaries;
    }

    @GetMapping("/decks")
    public String decks(@RequestParam(name = "card", required = false) List<Integer> cards,
            @RequestParam(required = false, defaultValue = "1") int page, Model model, Locale locale) {
        Map<Integer, CardsResponse.Card> cardsById = cardService.allById();
        List<Integer> selected = addCardFilter(cards, cardsById, model, locale);
        List<String> cardNames = cardNames(selected, cardsById, locale);

        // 初回の集計が終わるまで(デプロイ直後の1時間ほど)は空で、一覧の代わりに案内文を出す。
        Optional<TopPlayerDeckService.Page> found = topPlayerDeckService.page(selected, page);
        TopPlayerDeckService.Page pageOrNull = found.orElse(null);
        model.addAttribute("heading", pageSummaries.decksHeading(cardNames, locale));
        model.addAttribute("pageTitle", pageSummaries.decksTitle(cardNames, pageOrNull, locale));
        model.addAttribute("pageDescription", pageSummaries.decksSummary(cardNames, pageOrNull, locale));
        // トップ層で誰も使っていないカードの画面は中身が空なので、検索結果に出さない(sitemapからも外している)。
        // 2枚以上の組み合わせは数が限りなく、カード1枚の画面と中身も重なるので、検索結果には出さない(Xなどで直接共有する用)。
        model.addAttribute("noindex", selected.size() > 1
                || (selected.size() == 1 && pageOrNull != null && pageOrNull.total() == 0));
        found.ifPresent(result -> {
            model.addAttribute("decks", result.decks().stream()
                    .map(deck -> viewMapper.toTopPlayerDeck(deck, cardsById, locale))
                    .toList());
            addCollected(result.collectedAt(), result.finishedSeason(), model, locale);
            model.addAttribute("total", result.total());
            model.addAttribute("from", result.from());
            model.addAttribute("to", result.from() == 0 ? 0 : result.from() + result.decks().size() - 1);
            model.addAttribute("prevPage", result.page() > 1 ? result.page() - 1 : null);
            model.addAttribute("nextPage", result.hasNext() ? result.page() + 1 : null);
        });
        // 8枚とも選んだら束ねても1件で、この画面の一覧と同じなので、人気のデッキへの入口は出さない。
        model.addAttribute("showPopularLink", selected.size() < TopPlayerDeckService.MAX_FILTER_CARDS);
        return "decks";
    }

    /** 同じ8枚を2人以上が使っているデッキ。デッキ画面の一覧の前に置いていたが、画面が長く込み入るので分けた。 */
    @GetMapping("/decks/popular")
    public String popularDecks(@RequestParam(name = "card", required = false) List<Integer> cards,
            @RequestParam(required = false, defaultValue = "1") int page, Model model, Locale locale) {
        Map<Integer, CardsResponse.Card> cardsById = cardService.allById();
        List<Integer> selected = addCardFilter(cards, cardsById, model, locale);
        List<String> cardNames = cardNames(selected, cardsById, locale);

        Optional<TopPlayerDeckService.PopularPage> found = topPlayerDeckService.popular(selected, page);
        model.addAttribute("heading", pageSummaries.popularDecksHeading(cardNames, locale));
        model.addAttribute("pageTitle", pageSummaries.popularDecksTitle(cardNames, locale));
        model.addAttribute("pageDescription", pageSummaries.popularDecksSummary(cardNames, found.orElse(null), locale));
        // 絞り込んだ画面はデッキ画面のカード別の画面と中身が重なり、件数も少ないので検索結果には出さない。
        model.addAttribute("noindex", !selected.isEmpty());
        found.ifPresent(result -> {
            model.addAttribute("popularDecks", result.decks().stream()
                    .map(popular -> viewMapper.toPopularDeck(popular, cardsById, locale))
                    .toList());
            addCollected(result.collectedAt(), result.finishedSeason(), model, locale);
            model.addAttribute("total", result.total());
            model.addAttribute("from", result.from());
            model.addAttribute("to", result.from() == 0 ? 0 : result.from() + result.decks().size() - 1);
            model.addAttribute("prevPage", result.page() > 1 ? result.page() - 1 : null);
            model.addAttribute("nextPage", result.hasNext() ? result.page() + 1 : null);
        });
        return "popular-decks";
    }

    /** 上位どうしのランク戦で勝率の高いデッキ(2026-10-18に追加)。条件を満たすのは30種類ほどなので、ページ送りと絞り込みは付けない。 */
    @GetMapping("/decks/win-rates")
    public String winRateDecks(Model model, Locale locale) {
        Map<Integer, CardsResponse.Card> cardsById = cardService.allById();
        Optional<DeckWinRateService.Page> found = deckWinRateService.page();
        model.addAttribute("pageTitle", pageSummaries.winRateDecksTitle(locale));
        model.addAttribute("pageDescription", pageSummaries.winRateDecksSummary(found.orElse(null), locale));
        model.addAttribute("minBattles", DeckWinRates.MIN_BATTLES);
        model.addAttribute("minPlayers", DeckWinRates.MIN_PLAYERS);
        model.addAttribute("maxPlayerShare", DeckWinRates.MAX_PLAYER_SHARE_PERCENT);
        found.ifPresent(result -> {
            model.addAttribute("winRateDecks", viewMapper.toWinRateDecks(result.decks(), cardsById, locale));
            model.addAttribute("battles", result.battles());
            addCollected(result.collectedAt(), result.finishedSeason(), model, locale);
        });
        return "win-rate-decks";
    }

    /** 2つの画面で共通の「カードで絞り込む」欄。絞り込みに使うカードを返す。 */
    private List<Integer> addCardFilter(List<Integer> cards, Map<Integer, CardsResponse.Card> cardsById,
            Model model, Locale locale) {
        List<Integer> selected = TopPlayerDeckService.filterCards(cards == null ? List.of() : cards,
                cardsById.keySet());
        model.addAttribute("cardOptions", viewMapper.toCardOptions(cardsById.values(), locale));
        model.addAttribute("selectedCards", selected);
        model.addAttribute("filterRows", viewMapper.toDeckFilterRows(selected, TopPlayerDeckService.MAX_FILTER_CARDS));
        model.addAttribute("canAddCard", !selected.isEmpty() && selected.size() < TopPlayerDeckService.MAX_FILTER_CARDS);
        return selected;
    }

    private List<String> cardNames(List<Integer> selected, Map<Integer, CardsResponse.Card> cardsById, Locale locale) {
        return selected.stream()
                .map(id -> viewMapper.cardName(cardsById.get(id), locale))
                .toList();
    }

    private void addCollected(Instant collectedAt, YearMonth finishedSeason, Model model, Locale locale) {
        model.addAttribute("collectedAt", timeFormatter.instant(collectedAt, locale));
        model.addAttribute("seasonNotice", viewMapper.topDecksSeasonNotice(finishedSeason, locale));
    }
}
