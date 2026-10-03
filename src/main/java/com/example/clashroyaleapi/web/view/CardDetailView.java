package com.example.clashroyaleapi.web.view;

/**
 * カード詳細画面の表示内容。
 * elixirCost・evolutionIconUrl・heroIconUrl は、タワーユニットや進化・ヒーローの無いカードでは null になり、画面では出さない。
 */
public record CardDetailView(int id, String name, String englishName, String iconUrl, String evolutionIconUrl,
        String heroIconUrl, String rarity, Integer elixirCost, int minLevel, int maxLevel) {

    /** 通常の画像だけなら、どれがどの形かのラベルは要らない。 */
    public boolean hasOtherForms() {
        return evolutionIconUrl != null || heroIconUrl != null;
    }
}
