package com.example.clashroyaleapi.web.view;

import java.util.List;

/**
 * カード詳細画面の「〇〇入りのデッキが得意なカード」。
 *
 * @param battles        そのカードが片方のデッキだけに入っていた対戦の数
 * @param winRatePercent そのうち、そのカードを持たない側の勝率(全体の勝率)
 * @param from           数えた対戦(全カード分)のうち最も古いもの
 * @param to             数えた対戦(全カード分)のうち最も新しいもの
 */
public record CardCountersView(int battles, int winRatePercent, int players, int minBattles, TimeView from,
        TimeView to, List<CounterView> counters) {

    public record CounterView(int id, String name, String iconUrl, int battles, int winRatePercent) {
    }
}
