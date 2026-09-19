package com.example.clashroyaleapi.web.view;

import java.util.List;

/** カード一覧画面の1グループ(レアリティ、または最後のタワーユニット)。 */
public record CardCatalogGroupView(String heading, List<CardCatalogItemView> cards) {
}
