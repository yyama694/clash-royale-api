package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.client.exception.ClashRoyaleApiException;
import com.example.clashroyaleapi.domain.BattleInLog;
import com.example.clashroyaleapi.domain.CardCollection;
import com.example.clashroyaleapi.domain.CurrentDeck;
import com.example.clashroyaleapi.domain.GameText;
import com.example.clashroyaleapi.domain.PlayerBattleStats;
import com.example.clashroyaleapi.domain.PlayerNameSearch;
import com.example.clashroyaleapi.domain.PlayerSearchResult;
import com.example.clashroyaleapi.domain.SeasonRankLog;
import com.example.clashroyaleapi.domain.WinLoseStreak;
import com.example.clashroyaleapi.service.CardService;
import com.example.clashroyaleapi.service.PlayerService;
import com.example.clashroyaleapi.service.RankingHistoryService;
import com.example.clashroyaleapi.service.TopBattleService;
import com.example.clashroyaleapi.service.TopPlayerDeckService;
import com.example.clashroyaleapi.web.view.BattleDetailView;
import com.example.clashroyaleapi.web.view.CurrentDeckView;
import com.example.clashroyaleapi.web.view.RankHistoryView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/player")
public class PlayerController {

    private final PlayerService playerService;
    private final ViewMapper viewMapper;
    private final FavoriteCookies favoriteCookies;
    private final MeCookie meCookie;
    private final PageSummaries pageSummaries;
    private final TopPlayerDeckService topPlayerDeckService;
    private final CardService cardService;
    private final RankingHistoryService rankingHistoryService;
    private final TopBattleService topBattleService;

    public PlayerController(PlayerService playerService, ViewMapper viewMapper, FavoriteCookies favoriteCookies,
            MeCookie meCookie, PageSummaries pageSummaries, TopPlayerDeckService topPlayerDeckService,
            CardService cardService, RankingHistoryService rankingHistoryService, TopBattleService topBattleService) {
        this.playerService = playerService;
        this.viewMapper = viewMapper;
        this.favoriteCookies = favoriteCookies;
        this.meCookie = meCookie;
        this.pageSummaries = pageSummaries;
        this.topPlayerDeckService = topPlayerDeckService;
        this.cardService = cardService;
        this.rankingHistoryService = rankingHistoryService;
        this.topBattleService = topBattleService;
    }

    /** 検索フォームの受け口。タグで特定できたら正規化したタグのURLへ転送し、以降はブックマーク可能なパスで扱う。 */
    @GetMapping("/search")
    public String search(@RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "1") int page, Model model, Locale locale) {
        if (q == null || q.isBlank()) {
            return "redirect:/";
        }
        return switch (playerService.search(q, page)) {
            case PlayerSearchResult.Found found -> "redirect:/player/"
                    + UriUtils.encodePathSegment(Tags.toPathSegment(found.tag()), StandardCharsets.UTF_8);
            case PlayerSearchResult.Candidates candidates -> {
                PlayerNameSearch result = candidates.search();
                Map<String, PlayerResponse> details = playerService.candidateDetails(result);
                int shownTo = result.offset() + result.exact().size();
                int currentPage = result.offset() / PlayerService.SEARCH_PAGE_SIZE + 1;
                model.addAttribute("query", q.strip());
                model.addAttribute("exact", viewMapper.toPlayerNameMatches(result.exact(), details, locale));
                model.addAttribute("exactTotal", result.exactTotal());
                model.addAttribute("exactFrom", result.offset() + 1);
                model.addAttribute("exactTo", shownTo);
                model.addAttribute("prevPage", currentPage > 1 ? currentPage - 1 : null);
                model.addAttribute("nextPage", shownTo < result.exactTotal() ? currentPage + 1 : null);
                model.addAttribute("prefix", viewMapper.toPlayerNameMatches(result.prefix(), details, locale));
                model.addAttribute("morePrefix", result.morePrefix());
                yield "player-search";
            }
            case PlayerSearchResult.NotFound notFound -> {
                model.addAttribute("query", notFound.query());
                yield "player-search";
            }
        };
    }

    @GetMapping("/{tag}")
    public String player(@PathVariable String tag, @RequestParam(required = false) String favoriteLimit,
            Model model, Locale locale, HttpServletRequest request, HttpServletResponse response) {
        PlayerResponse player = playerService.findPlayer(tag);
        String playerName = GameText.stripFormatting(player.name());
        String playerPathTag = Tags.toPathSegment(player.tag());
        model.addAttribute("player", player);
        model.addAttribute("kingTowerLevel", viewMapper.kingTowerLevel(player));
        model.addAttribute("progress", viewMapper.toPlayerProgress(player));
        model.addAttribute("rankedLeague", viewMapper.toCurrentRankedLeague(player, locale));
        model.addAttribute("playerName", playerName);
        model.addAttribute("playerPathTag", playerPathTag);
        model.addAttribute("favorite",
                favoriteCookies.refreshName(request, response, FavoriteKind.PLAYER, playerPathTag, playerName)
                        .contains(playerPathTag));
        model.addAttribute("favoriteLimit", favoriteLimit != null);
        model.addAttribute("favoriteHint", !favoriteCookies.hasAny(request));
        model.addAttribute("isMe", meCookie.read(request).filter(playerPathTag::equals).isPresent());
        if (player.clan() != null) {
            model.addAttribute("clanName", GameText.stripFormatting(player.clan().name()));
            model.addAttribute("clanPathTag", Tags.toPathSegment(player.clan().tag()));
        }
        if (player.cards() != null && !player.cards().isEmpty()) {
            model.addAttribute("cardCollection", viewMapper.toCardCollection(CardCollection.of(player.cards()), locale));
        }

        // 戦績の取得に失敗してもプレイヤー情報自体は表示したいので、ここだけは個別に握る。
        List<BattleLogEntry> battleLog = List.of();
        PlayerBattleStats stats = null;
        try {
            battleLog = playerService.findBattleLog(tag);
            model.addAttribute("battles", viewMapper.toBattleSummaries(battleLog, player.tag(), locale));
            if (!battleLog.isEmpty()) {
                stats = playerService.statsOf(battleLog);
                model.addAttribute("battleStats",
                        viewMapper.toStats(stats, topBattleService.matchups().orElse(null), locale));
                model.addAttribute("elixirLeak", viewMapper.toElixirLeak(playerService.elixirLeakOf(battleLog),
                        topBattleService.elixirLeak().orElse(null), topBattleService.players()));
            }
        } catch (ClashRoyaleApiException e) {
            model.addAttribute("battleLogErrorKey", e.messageKey());
        }
        WinLoseStreak streak = WinLoseStreak.from(battleLog).orElse(null);
        if (streak != null) {
            model.addAttribute("streak", viewMapper.toStreakLabel(streak, locale));
        }
        CurrentDeck deck = playerService.currentDeckOf(player, battleLog).orElse(null);
        CurrentDeckView currentDeck = deck == null ? null : viewMapper.toCurrentDeck(deck, locale);
        model.addAttribute("currentDeck", currentDeck);
        if (deck != null) {
            List<Integer> cardIds = deck.cards().stream().map(BattleLogEntry.Card::id).toList();
            topPlayerDeckService.similar(cardIds, player.tag()).ifPresent(similar -> model.addAttribute(
                    "similarDecks", viewMapper.toSimilarDecks(similar, cardIds, cardService.allById(), locale)));
        }
        model.addAttribute("rankHistory", rankHistoryOf(player, locale));

        model.addAttribute("pageTitle", pageSummaries.playerTitle(player, playerName, locale));
        model.addAttribute("shareText", pageSummaries.playerShareText(player, playerName, streak, locale));
        model.addAttribute("pageDescription", pageSummaries.playerSummary(player, playerName, streak,
                currentDeck == null ? null : currentDeck.meta().averageElixir(), stats, locale));
        return "player";
    }

    /** 今シーズンに世界上位1000人に入ったことのある人だけ。それ以外は null で、欄ごと出さない。 */
    private RankHistoryView rankHistoryOf(PlayerResponse player, Locale locale) {
        List<SeasonRankLog.Point> points = rankingHistoryService.seasonHistory(player.tag());
        return points.isEmpty() ? null : viewMapper.toRankHistory(points, locale);
    }

    @GetMapping("/{tag}/battles/{battleTime}")
    public String battleDetail(@PathVariable String tag, @PathVariable String battleTime, Model model,
            Locale locale) {
        BattleInLog found = playerService.findBattle(tag, battleTime);
        model.addAttribute("playerPathTag", Tags.toPathSegment(tag));
        model.addAttribute("olderBattleTime", found.olderBattleTime());
        model.addAttribute("newerBattleTime", found.newerBattleTime());
        BattleDetailView view = viewMapper.toBattleDetail(found.battle(), tag, locale);
        model.addAttribute("battle", view);
        model.addAttribute("pageTitle", pageSummaries.battleTitle(view, locale));
        // 公式APIは直近の対戦しか返さないので、日がたつと404になる。検索結果に出しても行き止まりになるだけなので載せない。
        model.addAttribute("noindex", true);
        return "battle-detail";
    }
}
