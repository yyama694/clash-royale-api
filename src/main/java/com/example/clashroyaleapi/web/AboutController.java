package com.example.clashroyaleapi.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Locale;

/**
 * 運営者情報と問い合わせ先。運営者は「日本の個人」とだけ書き、本名・住所は載せない(2026-10-14にユーザーが決定)。
 * 問い合わせはXのDMだけで受ける(外部のフォームを増やさないため)。
 */
@Controller
public class AboutController {

    private final LabelResolver labels;

    public AboutController(LabelResolver labels) {
        this.labels = labels;
    }

    @GetMapping("/about")
    public String about(Model model, Locale locale) {
        model.addAttribute("pageDescription", labels.message("about.intro", locale));
        return "about";
    }
}
