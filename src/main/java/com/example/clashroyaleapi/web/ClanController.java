package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientResponseException;

@Controller
public class ClanController {

    private final ClashRoyaleApiClient clashRoyaleApiClient;

    public ClanController(ClashRoyaleApiClient clashRoyaleApiClient) {
        this.clashRoyaleApiClient = clashRoyaleApiClient;
    }

    @GetMapping("/clan")
    public String clan(@RequestParam(required = false) String tag, Model model) {
        if (tag == null || tag.isBlank()) {
            return "redirect:/";
        }
        try {
            model.addAttribute("clan", clashRoyaleApiClient.getClan(tag));
        } catch (RestClientResponseException e) {
            model.addAttribute("error", "クランが見つからないか、APIエラーが発生しました(" + e.getStatusCode() + ")");
        }
        return "clan";
    }
}
