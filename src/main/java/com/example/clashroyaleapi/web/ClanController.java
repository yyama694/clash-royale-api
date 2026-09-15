package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.ClanSearchResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientResponseException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Controller
public class ClanController {

    // 名前検索結果は多いと100件近く返るため、見やすさのためクランスコア順で上位のみ表示する。
    private static final int SEARCH_RESULT_LIMIT = 20;

    // 役職の「偉さ」の順序(昇順ソート時にリーダーが先頭に来るようにする)。
    private static final Map<String, Integer> ROLE_RANK = Map.of(
            "leader", 0,
            "coLeader", 1,
            "elder", 2,
            "member", 3
    );

    private final ClashRoyaleApiClient clashRoyaleApiClient;

    public ClanController(ClashRoyaleApiClient clashRoyaleApiClient) {
        this.clashRoyaleApiClient = clashRoyaleApiClient;
    }

    // "tag"パラメータ名は維持しつつ、クランタグ・クラン名のどちらでも検索できるようにする。
    // まずタグとして完全一致検索を試み、見つからなければ(404)クラン名の部分一致検索にフォールバックする。
    @GetMapping("/clan")
    public String clan(@RequestParam(required = false) String tag,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDir,
            Model model) {
        if (tag == null || tag.isBlank()) {
            return "redirect:/";
        }
        try {
            ClanResponse clan = clashRoyaleApiClient.getClan(tag);
            model.addAttribute("clan", clan);
            model.addAttribute("sortedMembers", sortMembers(clan.memberList(), sortBy, sortDir));
            model.addAttribute("sortBy", sortBy);
            model.addAttribute("sortDir", sortDir);
            return "clan";
        } catch (RestClientResponseException e) {
            // クラン名などタグとして不正な形式の場合、公式APIは404ではなく400を返すため、
            // 400・404のどちらも「タグとしては見つからなかった」とみなし、クラン名検索にフォールバックする。
            if (e.getStatusCode() != HttpStatus.NOT_FOUND && e.getStatusCode() != HttpStatus.BAD_REQUEST) {
                model.addAttribute("error", "クランが見つからないか、APIエラーが発生しました(" + e.getStatusCode() + ")");
                return "clan";
            }
        }

        // 公式APIはname 3文字未満だと400エラーになるため、部分一致検索の性質を利用して
        // 末尾に全角スペースを補い3文字以上にする(ユーザーが「償い」等の2文字クラン名で検索できるようにするため)。
        String searchName = tag;
        while (searchName.length() < 3) {
            searchName += "　";
        }
        try {
            List<ClanSearchResponse.ClanSummary> results = clashRoyaleApiClient.searchClansByName(searchName);
            if (results.isEmpty()) {
                model.addAttribute("error", "クランが見つかりませんでした(タグ・クラン名のどちらとしても一致しませんでした)。");
            } else {
                List<ClanSearchResponse.ClanSummary> top = results.stream()
                        .sorted(Comparator.comparingInt(ClanSearchResponse.ClanSummary::clanScore).reversed())
                        .limit(SEARCH_RESULT_LIMIT)
                        .toList();
                model.addAttribute("searchResults", top);
                model.addAttribute("searchResultTotal", results.size());
            }
        } catch (RestClientResponseException e) {
            model.addAttribute("error", "クランが見つからないか、APIエラーが発生しました(" + e.getStatusCode() + ")");
        }
        return "clan";
    }

    // sortByが未指定・不正な値の場合は公式APIが返す元の並び順のまま表示する。
    private List<ClanResponse.Member> sortMembers(List<ClanResponse.Member> members, String sortBy, String sortDir) {
        Comparator<ClanResponse.Member> comparator = switch (sortBy == null ? "" : sortBy) {
            case "name" -> Comparator.comparing(ClanResponse.Member::name, String.CASE_INSENSITIVE_ORDER);
            case "role" -> Comparator.comparingInt(m -> ROLE_RANK.getOrDefault(m.role(), Integer.MAX_VALUE));
            case "trophies" -> Comparator.comparingInt(ClanResponse.Member::trophies);
            case "donations" -> Comparator.comparingInt(ClanResponse.Member::donations);
            default -> null;
        };
        if (comparator == null) {
            return members;
        }
        if ("desc".equals(sortDir)) {
            comparator = comparator.reversed();
        }
        return members.stream().sorted(comparator).toList();
    }
}
