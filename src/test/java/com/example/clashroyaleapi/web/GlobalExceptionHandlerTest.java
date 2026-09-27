package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.exception.ApiMaintenanceException;
import com.example.clashroyaleapi.client.exception.ResourceNotFoundException;
import com.example.clashroyaleapi.config.PlayerIndexProperties;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new GlobalModelAttributes(new PlayerIndexProperties("data", false)));

    /**
     * @ExceptionHandler には @ControllerAdvice の @ModelAttribute が適用されない。
     * 属性が無いままだとテンプレートのhreflangがnull同士の連結で500になるため、ハンドラ側で詰めていることを確かめる。
     */
    @Test
    void エラー画面にも全画面共通の属性を詰める() {
        Model model = new ExtendedModelMap();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/player/ZZZ");
        request.setQueryString("lang=en");

        String view = handler.handleApiError(new ResourceNotFoundException("not found", null), model, request,
                new MockHttpServletResponse());

        assertEquals("error", view);
        assertEquals("/player/ZZZ", model.getAttribute("currentUri"));
        assertEquals("http://localhost", model.getAttribute("siteBaseUrl"));
    }

    /** タグを打ち間違えた人に、その場で名前から探せることを伝えるため(フォームの文言がこの属性で切り替わる)。 */
    @Test
    void 見つからない画面の検索フォームにも名前検索の設定を渡す() {
        GlobalExceptionHandler nameSearchHandler =
                new GlobalExceptionHandler(new GlobalModelAttributes(new PlayerIndexProperties("data", true)));
        Model model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        nameSearchHandler.handleApiError(
                new ResourceNotFoundException(ResourceNotFoundException.PLAYER, "not found", null), model,
                new MockHttpServletRequest("GET", "/player/ZZZ"), response);

        assertEquals(404, response.getStatus());
        assertEquals(true, model.getAttribute("nameSearchEnabled"));
        assertEquals(true, model.getAttribute("notFound"));
        assertEquals("error.playerNotFound", model.getAttribute("errorKey"));
    }

    @Test
    void メンテナンス中は503でメンテナンスの文言を出す() {
        Model model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handleApiError(new ApiMaintenanceException("in maintenance", null), model,
                new MockHttpServletRequest("GET", "/clan/YRPY9VQV"), response);

        assertEquals(503, response.getStatus());
        assertEquals("error.maintenance", model.getAttribute("errorKey"));
        assertEquals(false, model.getAttribute("notFound"));
    }

    @Test
    void 他サイトからのPOSTは403で不正なリクエストの文言を出す() {
        Model model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = handler.handleCrossSiteRequest(new CrossSiteRequestException("POST /favorites/players/AAA"),
                model, new MockHttpServletRequest("POST", "/favorites/players/AAA"), response);

        assertEquals("error", view);
        assertEquals(403, response.getStatus());
        assertEquals("error.badRequest", model.getAttribute("errorKey"));
    }
}
