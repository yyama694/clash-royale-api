package com.example.clashroyaleapi.web.view;

import java.util.List;

/**
 * @param notice 表の上に出す断り書き(取得できなかった・まだ誰もいない・終わったシーズンを代わりに出している)。
 *               今シーズンの順位をそのまま出すときは null
 */
public record PlayerRankingView(List<PlayerRankingRowView> rows, String notice) {
}
