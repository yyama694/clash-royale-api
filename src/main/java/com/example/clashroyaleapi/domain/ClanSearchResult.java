package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.ClanSearchResponse;

import java.util.List;

/**
 * クラン検索の結果。タグ完全一致・クラン名部分一致・該当なしの3通りを型で表し、
 * Controller側が「404ならフォールバック」のようなHTTPの都合を意識しなくて済むようにする。
 */
public sealed interface ClanSearchResult {

    /** タグとして一意に特定できた。 */
    record Found(ClanResponse clan) implements ClanSearchResult {
    }

    /** クラン名の部分一致で複数の候補が見つかった。totalは絞り込み前の件数。 */
    record Candidates(List<ClanSearchResponse.ClanSummary> clans, int total) implements ClanSearchResult {
    }

    /** タグ・クラン名のどちらとしても一致しなかった。 */
    record NotFound(String query) implements ClanSearchResult {
    }
}
