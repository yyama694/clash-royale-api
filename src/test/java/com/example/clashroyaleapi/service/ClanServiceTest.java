package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.ClanSearchResponse;
import com.example.clashroyaleapi.client.exception.ResourceNotFoundException;
import com.example.clashroyaleapi.domain.ClanSearchResult;
import com.example.clashroyaleapi.domain.MemberSortKey;
import com.example.clashroyaleapi.domain.SortDirection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Controllerから切り出したことで、Springコンテキストを起動せずに
 * タグ検索とクラン名検索の振り分けロジックを検証できるようになっている。
 */
class ClanServiceTest {

    private ClashRoyaleApiClient apiClient;
    private ClanService clanService;

    @BeforeEach
    void setUp() {
        apiClient = mock(ClashRoyaleApiClient.class);
        clanService = new ClanService(apiClient);
    }

    @Test
    void タグとして一致すればクランを返す() {
        ClanResponse clan = clan("#2XYZ456", "Royals");
        when(apiClient.getClan("2XYZ456")).thenReturn(clan);

        ClanSearchResult result = clanService.search("2XYZ456");

        ClanSearchResult.Found found = assertInstanceOf(ClanSearchResult.Found.class, result);
        assertEquals("Royals", found.clan().name());
    }

    @Test
    void タグとして見つからなければクラン名検索にフォールバックする() {
        when(apiClient.getClan(anyString())).thenThrow(new ResourceNotFoundException("not found", null));
        when(apiClient.searchClansByName(anyString())).thenReturn(List.of(summary("#A", "ABCDEF", 100, 10)));

        ClanSearchResult result = clanService.search("ABCDEF");

        assertInstanceOf(ClanSearchResult.Candidates.class, result);
    }

    @Test
    void 日本語のクラン名ではタグ検索を試みずに名前検索へ直行する() {
        when(apiClient.searchClansByName(anyString())).thenReturn(List.of(summary("#A", "償い", 100, 10)));

        clanService.search("償い");

        verify(apiClient, never()).getClan(anyString());
    }

    @Test
    void 三文字未満のクラン名は全角スペースで補ってから検索する() {
        // 公式APIはnameが3文字未満だと400になるため、部分一致検索の性質を利用して埋める。
        when(apiClient.searchClansByName(anyString())).thenReturn(List.of(summary("#A", "償い", 100, 10)));

        clanService.search("償い");

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(apiClient).searchClansByName(sent.capture());
        assertEquals("償い　", sent.getValue());
    }

    @Test
    void 絵文字入りのクラン名はコードポイント数で長さを数える() {
        // "A" + 絵文字 は2文字だが String#length() では3になる。length()基準だと補完されず400になってしまう。
        when(apiClient.searchClansByName(anyString())).thenReturn(List.of(summary("#A", "A🔥", 100, 10)));

        clanService.search("A🔥");

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(apiClient).searchClansByName(sent.capture());
        assertEquals("A🔥　", sent.getValue());
    }

    @Test
    void 名前検索の結果はクランスコア降順で上位のみ返す() {
        List<ClanSearchResponse.ClanSummary> many = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            many.add(summary("#C" + i, "clan" + i, i, 10));
        }
        when(apiClient.searchClansByName(anyString())).thenReturn(many);

        ClanSearchResult result = clanService.search("clan");

        ClanSearchResult.Candidates candidates = assertInstanceOf(ClanSearchResult.Candidates.class, result);
        assertEquals(20, candidates.clans().size());
        assertEquals(30, candidates.total());
        assertEquals(29, candidates.clans().get(0).clanScore());
    }

    @Test
    void どちらでも一致しなければNotFoundを返す() {
        when(apiClient.searchClansByName(anyString())).thenReturn(List.of());

        assertInstanceOf(ClanSearchResult.NotFound.class, clanService.search("存在しないクラン"));
    }

    @Test
    void ソート指定がなければAPIの並び順をそのまま返す() {
        List<ClanResponse.Member> members = List.of(member("B", "member", 100), member("A", "leader", 300));

        assertEquals(members, clanService.sortMembers(members, null, SortDirection.ASC));
    }

    @Test
    void 役職の昇順ではリーダーが先頭に来る() {
        List<ClanResponse.Member> members =
                List.of(member("B", "member", 100), member("A", "leader", 300), member("C", "elder", 200));

        List<ClanResponse.Member> sorted = clanService.sortMembers(members, MemberSortKey.ROLE, SortDirection.ASC);

        assertEquals(List.of("A", "C", "B"), sorted.stream().map(ClanResponse.Member::name).toList());
    }

    @Test
    void トロフィーの降順ソートができる() {
        List<ClanResponse.Member> members = List.of(member("B", "member", 100), member("A", "leader", 300));

        List<ClanResponse.Member> sorted =
                clanService.sortMembers(members, MemberSortKey.TROPHIES, SortDirection.DESC);

        assertEquals(List.of("A", "B"), sorted.stream().map(ClanResponse.Member::name).toList());
    }

    @Test
    void 最終アクセスの昇順では直近にアクセスしたメンバーが先頭に来る() {
        List<ClanResponse.Member> members = List.of(
                member("old", "member", 100, "20260101T000000.000Z"),
                member("today", "member", 100, "20260915T000000.000Z"),
                member("recent", "member", 100, "20260910T000000.000Z"));

        List<ClanResponse.Member> sorted =
                clanService.sortMembers(members, MemberSortKey.LAST_SEEN, SortDirection.ASC);

        assertEquals(List.of("today", "recent", "old"), sorted.stream().map(ClanResponse.Member::name).toList());
    }

    @Test
    void 最終アクセスの降順では非アクティブなメンバーが先頭に来る() {
        List<ClanResponse.Member> members = List.of(
                member("today", "member", 100, "20260915T000000.000Z"),
                member("old", "member", 100, "20260101T000000.000Z"));

        List<ClanResponse.Member> sorted =
                clanService.sortMembers(members, MemberSortKey.LAST_SEEN, SortDirection.DESC);

        assertEquals(List.of("old", "today"), sorted.stream().map(ClanResponse.Member::name).toList());
    }

    @Test
    void 最終アクセスが取得できないメンバーは昇順で末尾に置く() {
        List<ClanResponse.Member> members = List.of(
                member("unknown", "member", 100, null),
                member("today", "member", 100, "20260915T000000.000Z"));

        List<ClanResponse.Member> sorted =
                clanService.sortMembers(members, MemberSortKey.LAST_SEEN, SortDirection.ASC);

        assertEquals(List.of("today", "unknown"), sorted.stream().map(ClanResponse.Member::name).toList());
    }

    @Test
    void ソートキーはURL表記で解決できる() {
        assertEquals(MemberSortKey.LAST_SEEN, MemberSortKey.from("lastSeen").orElseThrow());
        assertEquals(MemberSortKey.TROPHIES, MemberSortKey.from("trophies").orElseThrow());
        assertTrue(MemberSortKey.from("bogus").isEmpty());
    }

    private static ClanResponse clan(String tag, String name) {
        return new ClanResponse(tag, name, "", 100, 1, List.of());
    }

    private static ClanSearchResponse.ClanSummary summary(String tag, String name, int score, int members) {
        return new ClanSearchResponse.ClanSummary(tag, name, score, members);
    }

    private static ClanResponse.Member member(String name, String role, int trophies) {
        return member(name, role, trophies, "20260915T000000.000Z");
    }

    private static ClanResponse.Member member(String name, String role, int trophies, String lastSeen) {
        return new ClanResponse.Member("#" + name, name, role, trophies, 0, lastSeen);
    }
}
