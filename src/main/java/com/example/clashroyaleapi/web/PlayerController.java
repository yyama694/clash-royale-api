package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.client.exception.ClashRoyaleApiException;
import com.example.clashroyaleapi.domain.CardCollection;
import com.example.clashroyaleapi.domain.GameText;
import com.example.clashroyaleapi.domain.PlayerNameSearch;
import com.example.clashroyaleapi.domain.PlayerSearchResult;
import com.example.clashroyaleapi.domain.WinLoseStreak;
import com.example.clashroyaleapi.service.PlayerService;

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

@Controller
@RequestMapping("/player")
public class PlayerController {

    private final PlayerService playerService;
    private final ViewMapper viewMapper;

    public PlayerController(PlayerService playerService, ViewMapper viewMapper) {
        this.playerService = playerService;
        this.viewMapper = viewMapper;
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
                int shownTo = result.offset() + result.exact().size();
                int currentPage = result.offset() / PlayerService.SEARCH_PAGE_SIZE + 1;
                model.addAttribute("query", q.strip());
                model.addAttribute("exact", viewMapper.toPlayerNameMatches(result.exact(), locale));
                model.addAttribute("exactTotal", result.exactTotal());
                model.addAttribute("exactFrom", result.offset() + 1);
                model.addAttribute("exactTo", shownTo);
                model.addAttribute("prevPage", currentPage > 1 ? currentPage - 1 : null);
                model.addAttribute("nextPage", shownTo < result.exactTotal() ? currentPage + 1 : null);
                model.addAttribute("prefix", viewMapper.toPlayerNameMatches(result.prefix(), locale));
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
    public String player(@PathVariable String tag, Model model, Locale locale) {
        PlayerResponse player = playerService.findPlayer(tag);
        model.addAttribute("player", player);
        model.addAttribute("playerName", GameText.stripFormatting(player.name()));
        model.addAttribute("playerPathTag", Tags.toPathSegment(player.tag()));
        if (player.clan() != null) {
            model.addAttribute("clanName", GameText.stripFormatting(player.clan().name()));
            model.addAttribute("clanPathTag", Tags.toPathSegment(player.clan().tag()));
        }
        WinLoseStreak.of(player.currentWinLoseStreak()).ifPresent(streak -> model.addAttribute("streak", streak));
        viewMapper.toCurrentDeck(player, locale).ifPresent(deck -> model.addAttribute("currentDeck", deck));
        if (player.cards() != null && !player.cards().isEmpty()) {
            model.addAttribute("cardCollection", viewMapper.toCardCollection(CardCollection.of(player.cards()), locale));
        }

        // 戦績の取得に失敗してもプレイヤー情報自体は表示したいので、ここだけは個別に握る。
        try {
            List<BattleLogEntry> battleLog = playerService.findBattleLog(tag);
            model.addAttribute("battles", viewMapper.toBattleSummaries(battleLog, player.tag(), locale));
            if (!battleLog.isEmpty()) {
                model.addAttribute("battleStats", viewMapper.toStats(playerService.statsOf(battleLog), locale));
            }
        } catch (ClashRoyaleApiException e) {
            model.addAttribute("battleLogErrorKey", e.messageKey());
        }
        return "player";
    }

    @GetMapping("/{tag}/battles/{battleTime}")
    public String battleDetail(@PathVariable String tag, @PathVariable String battleTime, Model model,
            Locale locale) {
        BattleLogEntry battle = playerService.findBattle(tag, battleTime);
        model.addAttribute("playerPathTag", Tags.toPathSegment(tag));
        model.addAttribute("battle", viewMapper.toBattleDetail(battle, tag, locale));
        return "battle-detail";
    }
}
