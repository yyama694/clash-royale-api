package com.example.clashroyaleapi.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 言語切替リンクが「今見ているページのまま言語だけ切り替える」ために、
 * 現在のURI(既存のクエリ付き、langは除く)を全テンプレートに渡す。
 */
@ControllerAdvice
public class GlobalModelAttributes {

    @ModelAttribute("currentUri")
    public String currentUri(HttpServletRequest request) {
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return request.getRequestURI();
        }
        String kept = Arrays.stream(query.split("&"))
                .filter(param -> !param.startsWith(WebConstants.LANGUAGE_PARAM + "="))
                .collect(Collectors.joining("&"));
        return kept.isEmpty() ? request.getRequestURI() : request.getRequestURI() + "?" + kept;
    }
}
