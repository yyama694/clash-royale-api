package com.example.clashroyaleapi.web.view;

/**
 * プロ・クリエイターの一覧の1人分。取得できなかった人は available が false で、肩書きだけ埋まる。
 *
 * @param inGameName ゲーム内の名前。活動名と同じなら null(同じ名前を2度出さない)
 * @param rank       ランク戦の世界順位。無ければ null
 * @param rating     ランク戦のレーティング。順位が無ければ null
 * @param deck       使用中のデッキ。求められなければ null
 * @param channelUrl 本人のチャンネル。無ければ null
 */
public record ProPlayerView(ProBadgeView badge, String pathTag, boolean available, String inGameName, Integer rank,
                            Integer rating, Integer trophies, CurrentDeckView deck, String channelUrl,
                            String channelService) {
}
