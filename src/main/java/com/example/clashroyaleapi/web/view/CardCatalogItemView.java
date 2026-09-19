package com.example.clashroyaleapi.web.view;

/**
 * カード一覧画面の1枚。englishName は絞り込みで英語名でも引けるようにするために持つ。
 * elixirCost はタワーユニットと鏡では null になり、画面では出さない。
 */
public record CardCatalogItemView(int id, String name, String englishName, String iconUrl, Integer elixirCost) {
}
