package com.example.clashroyaleapi.web.view;

/**
 * カード詳細画面の表示内容。
 * elixirCost・evolutionIconUrl・heroIconUrl は、タワーユニットや進化・ヒーローの無いカードでは null になり、画面では出さない。
 * ground は地上に出すと別のコストになるカード(スピリットエンプレス)の地上の形で、ほかのカードでは null。
 */
public record CardDetailView(int id, String name, String englishName, String iconUrl, String evolutionIconUrl,
        String heroIconUrl, Ground ground, String rarity, Integer elixirCost, int minLevel, int maxLevel) {

    /** ゲームでは別のカード(「スピリットエンプレス（地上）」)。iconUrl は画像を置いていなければ null。 */
    public record Ground(String name, String iconUrl, int elixirCost) {
    }

    /** 通常の画像だけなら、どれがどの形かのラベルは要らない。 */
    public boolean hasOtherForms() {
        return evolutionIconUrl != null || heroIconUrl != null || ground != null && ground.iconUrl() != null;
    }
}
