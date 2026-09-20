package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.domain.Favorites;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FavoriteCookiesTest {

    private FavoriteCookies favoriteCookies;

    @BeforeEach
    void setUp() {
        favoriteCookies = new FavoriteCookies();
    }

    @Test
    void 書いて読むと元に戻る() {
        Favorites favorites = Favorites.empty().add("AAA", "太郎").add("BBB", "次郎");
        MockHttpServletResponse response = new MockHttpServletResponse();

        favoriteCookies.write(response, FavoriteKind.PLAYER, favorites);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(response.getCookie(FavoriteKind.PLAYER.cookieName()));

        assertEquals(favorites.entries(), favoriteCookies.read(request, FavoriteKind.PLAYER).entries());
    }

    @Test
    void Cookieの属性() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        favoriteCookies.write(response, FavoriteKind.CLAN, Favorites.empty().add("AAA", "名前"));

        Cookie cookie = response.getCookie(FavoriteKind.CLAN.cookieName());
        assertEquals("/", cookie.getPath());
        assertTrue(cookie.isHttpOnly());
        assertEquals(365 * 24 * 60 * 60, cookie.getMaxAge());
    }

    @Test
    void Cookieが無ければ空() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertTrue(favoriteCookies.read(request, FavoriteKind.PLAYER).entries().isEmpty());
    }

    @Test
    void 壊れたBase64は空扱い() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(FavoriteKind.PLAYER.cookieName(), "1.***not-base64***"));

        assertTrue(favoriteCookies.read(request, FavoriteKind.PLAYER).entries().isEmpty());
    }

    @Test
    void 知らないバージョンは空扱い() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(FavoriteKind.PLAYER.cookieName(), "2.AAAA"));

        assertTrue(favoriteCookies.read(request, FavoriteKind.PLAYER).entries().isEmpty());
    }

    @Test
    void タグの形式でない行は無視する() {
        String body = "AAA\t太郎\n不正な行\nbb!\t変な名前\nBBB\t次郎";
        String value = "1." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(body.getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(FavoriteKind.PLAYER.cookieName(), value));

        List<Favorites.Entry> entries = favoriteCookies.read(request, FavoriteKind.PLAYER).entries();

        assertEquals(List.of(new Favorites.Entry("AAA", "太郎"), new Favorites.Entry("BBB", "次郎")), entries);
    }

    @Test
    void 件数が11件以上なら先頭10件だけ使う() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            if (i > 0) {
                body.append('\n');
            }
            body.append("TAG").append(i).append('\t').append("name").append(i);
        }
        String value = "1." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(body.toString().getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(FavoriteKind.PLAYER.cookieName(), value));

        assertEquals(Favorites.MAX_ENTRIES, favoriteCookies.read(request, FavoriteKind.PLAYER).entries().size());
    }

    @Test
    void 長すぎる名前は50文字で切る() {
        String longName = "あ".repeat(60);
        String body = "AAA\t" + longName;
        String value = "1." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(body.getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(FavoriteKind.PLAYER.cookieName(), value));

        String name = favoriteCookies.read(request, FavoriteKind.PLAYER).entries().get(0).name();

        assertEquals(50, name.length());
    }

    @Test
    void 登録済みで名前が変わっていれば書き直す() {
        MockHttpServletResponse writeResponse = new MockHttpServletResponse();
        favoriteCookies.write(writeResponse, FavoriteKind.PLAYER, Favorites.empty().add("AAA", "旧名前"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(writeResponse.getCookie(FavoriteKind.PLAYER.cookieName()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        Favorites result = favoriteCookies.refreshName(request, response, FavoriteKind.PLAYER, "AAA", "新名前");

        assertEquals("新名前", result.entries().get(0).name());
        assertTrue(response.getCookie(FavoriteKind.PLAYER.cookieName()) != null);
    }

    @Test
    void 名前が同じなら書き直さない() {
        MockHttpServletResponse writeResponse = new MockHttpServletResponse();
        favoriteCookies.write(writeResponse, FavoriteKind.PLAYER, Favorites.empty().add("AAA", "名前"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(writeResponse.getCookie(FavoriteKind.PLAYER.cookieName()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        favoriteCookies.refreshName(request, response, FavoriteKind.PLAYER, "AAA", "名前");

        assertEquals(null, response.getCookie(FavoriteKind.PLAYER.cookieName()));
    }
}
