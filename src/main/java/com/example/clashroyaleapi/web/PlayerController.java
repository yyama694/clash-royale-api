package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientResponseException;

@Controller
public class PlayerController {

    private final ClashRoyaleApiClient clashRoyaleApiClient;

    public PlayerController(ClashRoyaleApiClient clashRoyaleApiClient) {
        this.clashRoyaleApiClient = clashRoyaleApiClient;
    }

    @GetMapping("/player")
    public String player(@RequestParam(required = false) String tag, Model model) {
        if (tag == null || tag.isBlank()) {
            return "redirect:/";
        }
        try {
            model.addAttribute("player", clashRoyaleApiClient.getPlayer(tag));
        } catch (RestClientResponseException e) {
            model.addAttribute("error", "プレイヤーが見つからないか、APIエラーが発生しました(" + e.getStatusCode() + ")");
        }
        return "player";
    }
}
