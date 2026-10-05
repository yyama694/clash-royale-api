package com.example.clashroyaleapi.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Locale;

/**
 * プライバシーポリシー。本文は英語と日本語だけで(2026-10-04にユーザーが決定)、他の言語では英語版が出る。
 * 画面の言語と本文の言語が違うときに、本文の部分だけ lang を英語にするため、本文の言語を渡す。
 */
@Controller
public class PrivacyController {

    @GetMapping("/privacy")
    public String privacy(Model model, Locale locale) {
        model.addAttribute("policyLanguage", Locale.JAPANESE.getLanguage().equals(locale.getLanguage()) ? "ja" : "en");
        return "privacy";
    }
}
