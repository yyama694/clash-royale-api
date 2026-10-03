package com.example.clashroyaleapi.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 公式APIの /cards。items が通常のカード、supportItems がタワーユニット。
 * 公式APIはカードの説明文やHP・ダメージ等のステータスを返さないため、ここにあるのが取得できる全てになる。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CardsResponse(List<Card> items, List<Card> supportItems) {

    /** elixirCost と maxEvolutionLevel はタワーユニットや進化の無いカードには無いため、参照型にしている。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Card(int id, String name, int maxLevel, Integer maxEvolutionLevel, Integer elixirCost,
            String rarity, IconUrls iconUrls) {

        /** evolutionMedium は進化(限界突破)の画像で42枚、heroMedium はヒーローの画像で17枚にある(2026-10-03)。 */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record IconUrls(String medium, String evolutionMedium, String heroMedium) {
        }
    }
}
