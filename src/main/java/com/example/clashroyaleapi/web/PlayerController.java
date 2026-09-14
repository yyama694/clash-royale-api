package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Locale;

@Controller
public class PlayerController {

    private final ClashRoyaleApiClient clashRoyaleApiClient;

    public PlayerController(ClashRoyaleApiClient clashRoyaleApiClient) {
        this.clashRoyaleApiClient = clashRoyaleApiClient;
    }

    @GetMapping("/player")
    public String player(@RequestParam(required = false) String tag, Model model, Locale locale) {
        if (tag == null || tag.isBlank()) {
            return "redirect:/";
        }
        model.addAttribute("useJapaneseCardNames", "ja".equalsIgnoreCase(locale.getLanguage()));
        try {
            PlayerResponse player = clashRoyaleApiClient.getPlayer(tag);
            model.addAttribute("player", player);
        } catch (RestClientResponseException e) {
            model.addAttribute("error", "プレイヤーが見つからないか、APIエラーが発生しました(" + e.getStatusCode() + ")");
            return "player";
        }
        try {
            List<BattleLogEntry> battleLog = clashRoyaleApiClient.getBattleLog(tag);
            model.addAttribute("battleLog", battleLog);
            if (!battleLog.isEmpty()) {
                model.addAttribute("battleStats", PlayerBattleStats.from(battleLog));
            }
        } catch (RestClientResponseException e) {
            model.addAttribute("battleLogError", "戦績の取得に失敗しました(" + e.getStatusCode() + ")");
        }
        return "player";
    }

    @GetMapping("/player/battle")
    public String battleDetail(@RequestParam String tag, @RequestParam int index, Model model, Locale locale) {
        model.addAttribute("tag", tag);
        model.addAttribute("useJapaneseCardNames", "ja".equalsIgnoreCase(locale.getLanguage()));
        List<BattleLogEntry> battleLog;
        try {
            battleLog = clashRoyaleApiClient.getBattleLog(tag);
        } catch (RestClientResponseException e) {
            model.addAttribute("error", "戦績の取得に失敗しました(" + e.getStatusCode() + ")");
            return "battle-detail";
        }
        if (index < 0 || index >= battleLog.size()) {
            model.addAttribute("error", "指定された対戦が見つかりませんでした(対戦履歴が更新された可能性があります)。");
            return "battle-detail";
        }
        model.addAttribute("battle", battleLog.get(index));
        return "battle-detail";
    }
}
