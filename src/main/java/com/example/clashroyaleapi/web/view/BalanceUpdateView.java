package com.example.clashroyaleapi.web.view;

import java.util.List;

/**
 * バランス調整1回分。anchor は画面内のリンク先(実施日の "2026-10-06")。
 * baselineDate・baselineSampleSize は調整前の集計が無ければ null。nowSampleSize は今の集計が無ければ null。
 */
public record BalanceUpdateView(String anchor, String heading, String sourceUrl, List<KindGroup> groups,
        String baselineDate, Integer baselineSampleSize, Integer nowSampleSize) {

    /** カード詳細画面の「バランス調整」の1行。anchor・heading は調整1回分のもの。 */
    public record CardHistoryEntry(String anchor, String heading, CardChangeView change) {
    }

    public record KindGroup(String code, String label, List<CardChangeView> cards) {
    }

    /**
     * before・now は使用率(%、小数1桁)。分からなければ null。進化・ヒーローだけの変更は、使用率が通常の形と合算なので
     * showUsage を false にして出さない。
     */
    public record CardChangeView(int cardId, String name, String iconUrl, String formLabel, String kindCode,
            String kindLabel, List<String> lines, boolean showUsage, Double before, Double now) {

        public Double delta() {
            return before == null || now == null ? null : Math.round((now - before) * 10) / 10.0;
        }
    }
}
