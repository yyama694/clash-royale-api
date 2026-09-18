package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.client.exception.CardNotFoundException;

import org.springframework.stereotype.Service;

@Service
public class CardService {

    private final ClashRoyaleApiClient apiClient;

    public CardService(ClashRoyaleApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /** 公式APIにカードを1枚だけ引くAPIは無いため、一覧(キャッシュ済み)から探す。 */
    public CardsResponse.Card byId(int id) {
        return apiClient.getCards().stream()
                .filter(card -> card.id() == id)
                .findFirst()
                .orElseThrow(() -> new CardNotFoundException("card not found: " + id));
    }
}
