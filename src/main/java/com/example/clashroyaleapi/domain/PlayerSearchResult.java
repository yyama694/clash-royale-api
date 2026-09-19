package com.example.clashroyaleapi.domain;

/** プレイヤー検索の結果。タグで特定・名前の候補・該当なしの3通り(クラン検索の ClanSearchResult と同じ考え方)。 */
public sealed interface PlayerSearchResult {

    /** タグとして扱う。存在確認はプレイヤー情報画面に任せる場合がある。 */
    record Found(String tag) implements PlayerSearchResult {
    }

    /** 名前が一致した候補。 */
    record Candidates(PlayerNameSearch search) implements PlayerSearchResult {
    }

    /** タグ・名前のどちらとしても一致しなかった。 */
    record NotFound(String query) implements PlayerSearchResult {
    }
}
