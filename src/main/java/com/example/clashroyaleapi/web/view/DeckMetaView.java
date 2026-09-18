package com.example.clashroyaleapi.web.view;

/** デッキの指標とコピー用リンク。求められない項目は null(テンプレート側でその項目だけ出さない)。 */
public record DeckMetaView(Double averageElixir, Integer fourCardCycle, String copyUrl) {
}
