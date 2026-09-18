package com.example.clashroyaleapi.web.view;

/**
 * カード詳細画面の表示内容。
 * elixirCost と evolutionIconUrl は、タワーユニットや進化の無いカードでは null になり、画面では行ごと出さない。
 */
public record CardDetailView(int id, String name, String englishName, String iconUrl, String evolutionIconUrl,
        String rarity, Integer elixirCost, int minLevel, int maxLevel) {
}
