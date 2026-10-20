package com.example.clashroyaleapi.web.view;

import java.util.List;

/**
 * バランス調整1回分。anchor は画面内のリンク先で、調整ごとの画面のURLにも使う(実施日の "2026-10-06")。
 * sourceUrl・sourceLabel は表示言語の公式の記事があればそれ、無ければ英語版(「公式の発表(英語)」)。
 * digests は種類ごとの使用率の変化のまとめ(1種類1文)。
 * baselineDate・baselineSampleSize は調整前の集計が無ければ null。nowSampleSize は今の集計が無ければ null。
 * showDaysNotice・showNextNotice・showFormNotice は、「〇日後」「次の調整の前」・進化やヒーローの使用率の断り書きを出すか。
 */
public record BalanceUpdateView(String anchor, String heading, String sourceUrl, String sourceLabel,
        List<KindGroup> groups, List<String> digests, String baselineDate, Integer baselineSampleSize,
        Integer nowSampleSize, boolean showDaysNotice, boolean showNextNotice, boolean showFormNotice) {

    /** カード詳細画面の「バランス調整」の1行。anchor・heading は調整1回分のもの。 */
    public record CardHistoryEntry(String anchor, String heading, CardChangeView change) {
    }

    public record KindGroup(String code, String label, List<CardChangeView> cards) {
    }

    /** usage は「世界トップ層の使用率: 調整前 38.9% → 1日後 … → 今 34.3%(-4.6ポイント)」。調整後の集計が無ければ null。 */
    public record CardChangeView(int cardId, String name, String iconUrl, String formLabel, String kindCode,
            String kindLabel, List<String> lines, String usage) {
    }
}
