package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.domain.BalanceUpdate;
import com.example.clashroyaleapi.domain.CardStats;
import com.example.clashroyaleapi.domain.CardUsage;
import com.example.clashroyaleapi.service.BalanceService;
import com.example.clashroyaleapi.service.CardService;
import com.example.clashroyaleapi.service.CardStatsService;
import com.example.clashroyaleapi.service.CardUsageService;
import com.example.clashroyaleapi.service.TopBattleService;
import com.example.clashroyaleapi.service.TopPlayerDeckService;
import com.example.clashroyaleapi.web.view.BalanceUpdateView;
import com.example.clashroyaleapi.web.view.CardCatalogGroupView;
import com.example.clashroyaleapi.web.view.CardDetailView;
import com.example.clashroyaleapi.web.view.CardUsageView;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Controller
public class CardController {

    /** カード詳細画面に出すトッププレイヤーのデッキの数。人気のカードは数百人が使っていて、全員だと画面が長くなりすぎる。 */
    static final int TOP_DECKS_ON_CARD_PAGE = 5;
    /** カード一覧を使用率の順(使用率ランキング)で出すときの {@code ?sort=} の値。 */
    static final String SORT_BY_USAGE = "usage";

    private final CardService cardService;
    private final CardUsageService cardUsageService;
    private final TopPlayerDeckService topPlayerDeckService;
    private final ViewMapper viewMapper;
    private final PageSummaries pageSummaries;
    private final TimeFormatter timeFormatter;
    private final BalanceService balanceService;
    private final TopBattleService topBattleService;
    private final CardStatsService cardStatsService;

    public CardController(CardService cardService, CardUsageService cardUsageService,
            TopPlayerDeckService topPlayerDeckService, ViewMapper viewMapper, PageSummaries pageSummaries,
            TimeFormatter timeFormatter, BalanceService balanceService, TopBattleService topBattleService,
            CardStatsService cardStatsService) {
        this.cardService = cardService;
        this.cardUsageService = cardUsageService;
        this.topPlayerDeckService = topPlayerDeckService;
        this.viewMapper = viewMapper;
        this.pageSummaries = pageSummaries;
        this.timeFormatter = timeFormatter;
        this.balanceService = balanceService;
        this.topBattleService = topBattleService;
        this.cardStatsService = cardStatsService;
    }

    @GetMapping("/cards")
    public String cards(@RequestParam(required = false) String sort, Model model, Locale locale) {
        // 初回の集計が終わるまで(デプロイ直後の1時間ほど)は使用率が無いので、切り替えを出さずレアリティ別だけにする。
        Optional<CardUsage> usage = cardUsageService.current();
        boolean byUsage = SORT_BY_USAGE.equals(sort) && usage.isPresent();
        model.addAttribute("usageAvailable", usage.isPresent());
        model.addAttribute("byUsage", byUsage);
        if (byUsage) {
            CardUsage current = usage.get();
            CardUsage weekAgo = cardUsageService.weekAgo().orElse(null);
            List<CardCatalogGroupView> groups = viewMapper.toCardUsageRanking(cardService.byUsage(current), current,
                    weekAgo, locale);
            model.addAttribute("usageChangeNotice",
                    weekAgo == null ? null : viewMapper.usageChangeNotice(weekAgo, locale));
            model.addAttribute("groups", groups);
            model.addAttribute("pageTitle", pageSummaries.cardUsageRankingTitle(locale));
            model.addAttribute("pageDescription",
                    pageSummaries.cardUsageRankingSummary(current.sampleSize(), groups, locale));
            model.addAttribute("sampleSize", current.sampleSize());
            model.addAttribute("collectedAt", timeFormatter.instant(current.collectedAt(), locale));
            model.addAttribute("seasonNotice", viewMapper.topDecksSeasonNotice(current.finishedSeason(), locale));
        } else {
            model.addAttribute("groups", viewMapper.toCardCatalog(cardService.catalog(), locale));
        }
        model.addAttribute("elixirCosts", cardService.elixirCosts());
        return "cards";
    }

    @GetMapping("/card/{id}")
    public String card(@PathVariable int id, Model model, Locale locale) {
        CardDetailView card = viewMapper.toCardDetail(cardService.byId(id), locale);
        Map<Integer, CardsResponse.Card> cardsById = cardService.allById();
        model.addAttribute("card", card);
        model.addAttribute("pageTitle", pageSummaries.cardTitle(card.name(), locale));
        CardStats stats = cardStatsService.stats();
        model.addAttribute("cardStats", viewMapper.toCardStats(stats.of(card.englishName()), stats.asOf(), locale));
        // 初回の集計が終わるまで(デプロイ直後の1時間ほど)は、使用率の欄ごと出さない。説明文もサイト共通のものになる。
        cardUsageService.current().ifPresent(usage -> {
            CardUsageView usageView = viewMapper.toCardUsage(usage, id, cardsById, locale);
            model.addAttribute("usage", usageView);
            model.addAttribute("pageDescription", pageSummaries.cardSummary(card.name(), usageView, locale));
        });
        topBattleService.matchups()
                .flatMap(matchups -> viewMapper.toCardMatchups(matchups, id, topBattleService.players(), cardsById,
                        locale))
                .ifPresent(matchups -> model.addAttribute("matchups", matchups));
        model.addAttribute("balanceHistory", balanceHistory(card.englishName(), id, locale));
        // 全員分はデッキ画面(カードで絞り込み)にあるので、ここでは順位の高い数人だけを出してそこへつなぐ。
        topPlayerDeckService.page(List.of(id), 1, TOP_DECKS_ON_CARD_PAGE).ifPresent(page -> {
            model.addAttribute("topDecks", page.decks().stream()
                    .map(deck -> viewMapper.toTopPlayerDeck(deck, cardsById, locale))
                    .toList());
            model.addAttribute("topDecksTotal", page.total());
        });
        return "card";
    }

    /** このカードに触れたバランス調整(新しい順)。進化・ヒーローだけの変更も含む。 */
    private List<BalanceUpdateView.CardHistoryEntry> balanceHistory(String englishName, int id, Locale locale) {
        List<BalanceUpdate> updates = balanceService.updatesOf(englishName);
        if (updates.isEmpty()) {
            return List.of();
        }
        Map<String, CardsResponse.Card> cardsByName = cardService.cardsByName();
        return updates.stream()
                .map(update -> viewMapper.toBalanceUpdate(update, balanceService.usage(update), cardsByName, locale))
                .flatMap(view -> view.groups().stream()
                        .flatMap(group -> group.cards().stream())
                        .filter(change -> change.cardId() == id)
                        .map(change -> new BalanceUpdateView.CardHistoryEntry(view.anchor(), view.heading(), change)))
                .toList();
    }
}
