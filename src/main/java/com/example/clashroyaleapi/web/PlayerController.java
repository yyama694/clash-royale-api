package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.client.exception.ClashRoyaleApiException;
import com.example.clashroyaleapi.domain.GameText;
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

    /** 検索フォームの受け口。正規化したタグのURLへ転送し、以降はブックマーク可能なパスで扱う。 */
    @GetMapping("/search")
    public String search(@RequestParam(required = false) String q) {
        if (q == null || q.isBlank()) {
            return "redirect:/";
        }
        return "redirect:/player/" + UriUtils.encodePathSegment(Tags.toPathSegment(q), StandardCharsets.UTF_8);
    }

    @GetMapping("/{tag}")
    public String player(@PathVariable String tag, Model model, Locale locale) {
        PlayerResponse player = playerService.findPlayer(tag);
        model.addAttribute("player", player);
        model.addAttribute("playerName", GameText.stripFormatting(player.name()));
        model.addAttribute("playerPathTag", Tags.toPathSegment(player.tag()));
        model.addAttribute("cardOrigin", CardOrigin.player(player.tag()));
        if (player.clan() != null) {
            model.addAttribute("clanName", GameText.stripFormatting(player.clan().name()));
            model.addAttribute("clanPathTag", Tags.toPathSegment(player.clan().tag()));
        }

        // 戦績の取得に失敗してもプレイヤー情報自体は表示したいので、ここだけは個別に握る。
        try {
            List<BattleLogEntry> battleLog = playerService.findBattleLog(tag);
            model.addAttribute("battles", viewMapper.toBattleSummaries(battleLog, player.tag(), locale));
            playerService.latestOneOnOne(battleLog)
                    .ifPresent(battle -> model.addAttribute("currentDeck", viewMapper.toCurrentDeck(battle, locale)));
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
        model.addAttribute("cardOrigin", CardOrigin.battle(tag, battleTime));
        model.addAttribute("battle", viewMapper.toBattleDetail(battle, tag, locale));
        return "battle-detail";
    }
}
