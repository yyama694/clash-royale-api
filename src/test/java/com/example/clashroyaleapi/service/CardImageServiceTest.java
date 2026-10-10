package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.CardImageClient;
import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.client.exception.ApiUnavailableException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CardImageServiceTest {

    private ClashRoyaleApiClient apiClient;
    private CardImageClient imageClient;
    private CardImageService service;

    @BeforeEach
    void setUp() {
        apiClient = mock(ClashRoyaleApiClient.class);
        imageClient = mock(CardImageClient.class);
        when(apiClient.getCards()).thenReturn(new CardsResponse(List.of(
                card(1, "knight.png", "knight-evo.png", "knight-hero.png"),
                card(2, "giant.png", "giant-evo.png", "giant-hero.png"),
                card(3, "zap.png", null, null)), List.of()));
        when(imageClient.exists(anyString())).thenReturn(true);
        service = new CardImageService(apiClient, imageClient, Map.of("giant-hero.png", "/images/giant-hero.png"),
                path -> path.replace(".png", "-abc123.png"));
    }

    @Test
    void 置き場所に無い進化とヒーローの画像だけを使わない() {
        when(imageClient.exists("giant-evo.png")).thenReturn(false);

        service.refresh();

        assertNull(service.usable("giant-evo.png"));
        assertEquals("knight-evo.png", service.usable("knight-evo.png"));
        assertEquals("knight-hero.png", service.usable("knight-hero.png"));
        assertNull(service.usable(null));
    }

    @Test
    void 確かめる前はURLをそのまま使う() {
        assertEquals("giant-evo.png", service.usable("giant-evo.png"));
    }

    @Test
    void 後から置き場所に画像が置かれれば使う() {
        when(imageClient.exists("giant-evo.png")).thenReturn(false);
        service.refresh();
        when(imageClient.exists("giant-evo.png")).thenReturn(true);

        service.refresh();

        assertEquals("giant-evo.png", service.usable("giant-evo.png"));
    }

    @Test
    void 置き場所に繋がらなければ前回の結果のままにする() {
        when(imageClient.exists("giant-evo.png")).thenReturn(false);
        service.refresh();
        when(imageClient.exists(anyString())).thenThrow(new ApiUnavailableException("timeout", null));

        service.refresh();

        assertNull(service.usable("giant-evo.png"));
        assertEquals("knight-evo.png", service.usable("knight-evo.png"));
    }

    @Test
    void カード一覧が取れなければ前回の結果のままにする() {
        when(imageClient.exists("giant-evo.png")).thenReturn(false);
        service.refresh();
        when(apiClient.getCards()).thenThrow(new ApiUnavailableException("timeout", null));

        service.refresh();

        assertNull(service.usable("giant-evo.png"));
    }

    @Test
    void 置き場所に無い画像は代わりの画像があればそれを使う() {
        when(imageClient.exists("giant-hero.png")).thenReturn(false);
        service.refresh();

        assertEquals("/images/giant-hero-abc123.png", service.usable("giant-hero.png"));
    }

    @Test
    void 代わりの画像があっても置き場所にあれば公式の画像を使う() {
        service.refresh();

        assertEquals("giant-hero.png", service.usable("giant-hero.png"));
    }

    private static CardsResponse.Card card(int id, String medium, String evolution, String hero) {
        return new CardsResponse.Card(id, "Card" + id, 16, null, 3, "common",
                new CardsResponse.Card.IconUrls(medium, evolution, hero));
    }
}
