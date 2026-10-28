package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.service.CardService;
import com.example.clashroyaleapi.service.CardStatsService;
import com.example.clashroyaleapi.service.QuizService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Locale;

/**
 * クラロワクイズ(設計_クイズ.md)。問題が増えるまで公開しない隠し機能で、どこからもリンクせず、
 * sitemapにも載せず、noindexにする。robots.txtには書かない(誰でも読めるので、書くとURLを教えることになる)。
 * 日本語だけで作っていて、表示言語は SupportedLanguages#fixedLanguage で日本語に固定している。
 */
@Controller
public class QuizController {

    private final QuizService quizService;
    private final CardService cardService;
    private final CardStatsService cardStatsService;
    private final ViewMapper viewMapper;
    private final LabelResolver labels;

    public QuizController(QuizService quizService, CardService cardService, CardStatsService cardStatsService,
            ViewMapper viewMapper, LabelResolver labels) {
        this.quizService = quizService;
        this.cardService = cardService;
        this.cardStatsService = cardStatsService;
        this.viewMapper = viewMapper;
        this.labels = labels;
    }

    @GetMapping("/quiz")
    public String quiz(Model model, Locale locale) {
        model.addAttribute("quiz", viewMapper.toQuiz(quizService.today(), cardService.allById(),
                cardStatsService.stats(), locale));
        model.addAttribute("noindex", true);
        model.addAttribute("pageDescription", labels.message("quiz.description", locale));
        return "quiz";
    }
}
