package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.ClanSearchResponse;
import com.example.clashroyaleapi.client.exception.ResourceNotFoundException;
import com.example.clashroyaleapi.domain.ClanSearchResult;
import com.example.clashroyaleapi.domain.MemberSortKey;
import com.example.clashroyaleapi.domain.PlayerSighting;
import com.example.clashroyaleapi.domain.SortDirection;
import com.example.clashroyaleapi.store.PlayerSightingLog;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class ClanService {

    // 名前検索結果は多いと100件近く返るため、見やすさのためクランスコア順で上位のみ表示する。
    private static final int SEARCH_RESULT_LIMIT = 20;
    // 公式APIはnameが3文字未満だと400エラーになる。
    private static final int MIN_SEARCH_NAME_LENGTH = 3;

    private final ClashRoyaleApiClient apiClient;
    private final PlayerSightingLog sightingLog;

    public ClanService(ClashRoyaleApiClient apiClient, PlayerSightingLog sightingLog) {
        this.apiClient = apiClient;
        this.sightingLog = sightingLog;
    }

    public ClanResponse findClan(String tag) {
        return recordMembers(apiClient.getClan(tag));
    }

    /**
     * クランタグ・クラン名のどちらでも検索できるようにする。
     * タグとして成立する形式ならまずタグ検索を試み、空振りしたらクラン名の部分一致検索にフォールバックする。
     */
    public ClanSearchResult search(String query) {
        if (query == null || query.isBlank()) {
            return new ClanSearchResult.NotFound("");
        }
        String trimmed = query.strip();
        if (Tags.looksLikeTag(trimmed)) {
            Optional<ClanResponse> clan = findClanOrEmpty(trimmed);
            if (clan.isPresent()) {
                return new ClanSearchResult.Found(clan.get());
            }
        }
        return searchByName(trimmed);
    }

    public List<ClanResponse.Member> sortMembers(List<ClanResponse.Member> members, MemberSortKey sortKey,
            SortDirection direction) {
        if (members == null) {
            return List.of();
        }
        // ソート指定が無い場合は公式APIが返す元の並び順(トロフィー降順)をそのまま使う。
        if (sortKey == null) {
            return members;
        }
        Comparator<ClanResponse.Member> comparator = sortKey.comparator(direction);
        return members.stream().sorted(comparator).toList();
    }

    private Optional<ClanResponse> findClanOrEmpty(String tag) {
        try {
            return Optional.ofNullable(apiClient.getClan(tag)).map(this::recordMembers);
        } catch (ResourceNotFoundException e) {
            return Optional.empty();
        }
    }

    private ClanResponse recordMembers(ClanResponse clan) {
        if (clan != null && clan.memberList() != null) {
            sightingLog.record(clan.memberList().stream()
                    .map(member -> new PlayerSighting(member.tag(), member.name()))
                    .toList());
        }
        return clan;
    }

    private ClanSearchResult searchByName(String name) {
        List<ClanSearchResponse.ClanSummary> results = apiClient.searchClansByName(padToMinimumLength(name));
        if (results.isEmpty()) {
            return new ClanSearchResult.NotFound(name);
        }
        List<ClanSearchResponse.ClanSummary> top = results.stream()
                .sorted(Comparator.comparingInt(ClanSearchResponse.ClanSummary::clanScore).reversed())
                .limit(SEARCH_RESULT_LIMIT)
                .toList();
        return new ClanSearchResult.Candidates(top, results.size());
    }

    /**
     * 「償い」のような2文字のクラン名でも検索できるよう、部分一致検索の性質を利用して
     * 末尾に全角スペースを補い最低文字数を満たす。
     * 絵文字入りのクラン名でも正しく数えるため、String#length()(UTF-16単位)ではなくコードポイント数で判定する。
     */
    private String padToMinimumLength(String name) {
        int length = name.codePointCount(0, name.length());
        if (length >= MIN_SEARCH_NAME_LENGTH) {
            return name;
        }
        return name + "　".repeat(MIN_SEARCH_NAME_LENGTH - length);
    }
}
