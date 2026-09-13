package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.nameindex.NameIndexService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientResponseException;

@Controller
public class ClanController {

    private final ClashRoyaleApiClient clashRoyaleApiClient;
    private final NameIndexService nameIndexService;

    public ClanController(ClashRoyaleApiClient clashRoyaleApiClient, NameIndexService nameIndexService) {
        this.clashRoyaleApiClient = clashRoyaleApiClient;
        this.nameIndexService = nameIndexService;
    }

    @GetMapping("/clan")
    public String clan(@RequestParam(required = false) String tag, Model model) {
        if (tag == null || tag.isBlank()) {
            return "redirect:/";
        }
        try {
            ClanResponse clan = clashRoyaleApiClient.getClan(tag);
            model.addAttribute("clan", clan);
            nameIndexService.register(clan.tag(), clan.name());
            clan.memberList().forEach(m -> nameIndexService.register(m.tag(), m.name()));
        } catch (RestClientResponseException e) {
            model.addAttribute("error", "クランが見つからないか、APIエラーが発生しました(" + e.getStatusCode() + ")");
        }
        return "clan";
    }
}
