package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.exception.ApiAccessDeniedException;
import com.example.clashroyaleapi.client.exception.ApiRateLimitException;
import com.example.clashroyaleapi.client.exception.ApiUnavailableException;
import com.example.clashroyaleapi.client.exception.BattleNotFoundException;
import com.example.clashroyaleapi.client.exception.ClashRoyaleApiException;
import com.example.clashroyaleapi.client.exception.ResourceNotFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * エラー画面の組み立てを1箇所に集約する。
 * 表示文言は例外が持つメッセージキーから決まるため、原因(404/403/429/通信エラー)ごとに自然に出し分けられる。
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // @ExceptionHandler には @ControllerAdvice の @ModelAttribute が適用されないため、
    // 全画面共通の属性(言語切替リンクとhreflangが使う)をここで自分で詰める。
    private final GlobalModelAttributes modelAttributes;

    public GlobalExceptionHandler(GlobalModelAttributes modelAttributes) {
        this.modelAttributes = modelAttributes;
    }

    @ExceptionHandler(ClashRoyaleApiException.class)
    public String handleApiError(ClashRoyaleApiException e, Model model, HttpServletRequest request,
            HttpServletResponse response) {
        HttpStatus status = statusOf(e);
        if (status.is5xxServerError()) {
            log.warn("Clash Royale API call failed: {}", e.getMessage(), e);
        }
        response.setStatus(status.value());
        model.addAttribute("errorKey", e.messageKey());
        // 「見つからない」は利用者の入力の問題でシステム障害ではないため、画面では赤いエラー表示にせず再検索を促す。
        model.addAttribute("notFound", status == HttpStatus.NOT_FOUND);
        return errorView(model, request);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public String handleBadRequest(Exception e, Model model, HttpServletRequest request,
            HttpServletResponse response) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        model.addAttribute("errorKey", "error.badRequest");
        return errorView(model, request);
    }

    private String errorView(Model model, HttpServletRequest request) {
        model.addAttribute("currentUri", modelAttributes.currentUri(request));
        model.addAttribute("siteBaseUrl", modelAttributes.siteBaseUrl(request));
        return "error";
    }

    private HttpStatus statusOf(ClashRoyaleApiException e) {
        if (e instanceof ResourceNotFoundException || e instanceof BattleNotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (e instanceof ApiRateLimitException) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        // 403は公式APIのIP許可リスト起因、つまりこちら側の設定不備なので利用者には5xxとして見せる。
        if (e instanceof ApiAccessDeniedException || e instanceof ApiUnavailableException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
