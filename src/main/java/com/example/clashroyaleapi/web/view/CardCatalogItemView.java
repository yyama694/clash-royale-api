package com.example.clashroyaleapi.web.view;

/**
 * カード一覧画面の1枚。englishName は絞り込みで英語名でも引けるようにするために持つ。
 * elixir はバッジの表示用で、タワーユニットでは null(コストの概念が無い)。
 * elixirCost は絞り込みの突き合わせ用の数値で、鏡・タワーユニットでは null。
 */
public record CardCatalogItemView(int id, String name, String englishName, String iconUrl,
                                  ElixirBadgeView elixir, Integer elixirCost) {
}
