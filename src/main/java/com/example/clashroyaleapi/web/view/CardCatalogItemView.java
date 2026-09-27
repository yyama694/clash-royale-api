package com.example.clashroyaleapi.web.view;

/**
 * カード一覧画面の1枚。searchText は絞り込み用で、表示名に加えて英語名と通称(あれば)でも引けるようにする。
 * elixir はバッジの表示用で、タワーユニットでは null(コストの概念が無い)。
 * elixirCost は絞り込みの突き合わせ用の数値で、鏡・タワーユニットでは null。
 */
public record CardCatalogItemView(int id, String name, String searchText, String iconUrl,
                                  ElixirBadgeView elixir, Integer elixirCost) {
}
