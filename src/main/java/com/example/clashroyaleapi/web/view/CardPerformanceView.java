package com.example.clashroyaleapi.web.view;

/** counters は、カード詳細画面にそのカードへの「勝っているカード」が出るか(苦手カードからそこへつなぐ)。 */
public record CardPerformanceView(int cardId, String name, String iconUrl, int uses, int wins, int winRatePercent,
        boolean counters) {
}
