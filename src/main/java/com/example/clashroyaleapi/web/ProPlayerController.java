package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.service.ProPlayerService;
import com.example.clashroyaleapi.web.view.ProPlayerView;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Locale;

@Controller
public class ProPlayerController {

    private final ProPlayerService proPlayerService;
    private final ViewMapper viewMapper;
    private final PageSummaries pageSummaries;

    public ProPlayerController(ProPlayerService proPlayerService, ViewMapper viewMapper, PageSummaries pageSummaries) {
        this.proPlayerService = proPlayerService;
        this.viewMapper = viewMapper;
        this.pageSummaries = pageSummaries;
    }

    @GetMapping("/pros")
    public String pros(Model model, Locale locale) {
        List<ProPlayerView> pros = viewMapper.toProPlayers(proPlayerService.current(), locale);
        model.addAttribute("pros", pros);
        model.addAttribute("pageTitle", pageSummaries.prosTitle(locale));
        model.addAttribute("pageDescription", pageSummaries.prosSummary(pros, locale));
        return "pros";
    }
}
