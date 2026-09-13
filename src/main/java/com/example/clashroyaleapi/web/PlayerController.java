package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.nameindex.NameIndexService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientResponseException;

@Controller
public class PlayerController {

    private final ClashRoyaleApiClient clashRoyaleApiClient;
    private final NameIndexService nameIndexService;

    public PlayerController(ClashRoyaleApiClient clashRoyaleApiClient, NameIndexService nameIndexService) {
        this.clashRoyaleApiClient = clashRoyaleApiClient;
        this.nameIndexService = nameIndexService;
    }

    @GetMapping("/player")
    public String player(@RequestParam(required = false) String tag, Model model) {
        if (tag == null || tag.isBlank()) {
            return "redirect:/";
        }
        try {
            PlayerResponse player = clashRoyaleApiClient.getPlayer(tag);
            model.addAttribute("player", player);
            nameIndexService.register(player.tag(), player.name());
        } catch (RestClientResponseException e) {
            model.addAttribute("error", "プレイヤーが見つからないか、APIエラーが発生しました(" + e.getStatusCode() + ")");
            return "player";
        }
        try {
            model.addAttribute("battleLog", clashRoyaleApiClient.getBattleLog(tag));
        } catch (RestClientResponseException e) {
            model.addAttribute("battleLogError", "戦績の取得に失敗しました(" + e.getStatusCode() + ")");
        }
        return "player";
    }
}
