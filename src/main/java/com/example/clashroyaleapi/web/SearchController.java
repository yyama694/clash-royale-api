package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.nameindex.NameIndexService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SearchController {

    private final NameIndexService nameIndexService;

    public SearchController(NameIndexService nameIndexService) {
        this.nameIndexService = nameIndexService;
    }

    @GetMapping("/search")
    public String search(@RequestParam(required = false) String name, Model model) {
        model.addAttribute("query", name);
        if (name != null && !name.isBlank()) {
            model.addAttribute("results", nameIndexService.search(name));
        }
        return "search";
    }
}
