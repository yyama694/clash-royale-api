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
import java.util.Locale;

@Controller
public class ClanController {

    // 名前検索結果は多いと100件近く返るため、見やすさのためクランスコア順で上位のみ表示する。
    private static final int SEARCH_RESULT_LIMIT = 20;

    private final ClashRoyaleApiClient clashRoyaleApiClient;

    public ClanController(ClashRoyaleApiClient clashRoyaleApiClient) {
        this.clashRoyaleApiClient = clashRoyaleApiClient;
    }

    // "tag"パラメータ名は維持しつつ、クランタグ・クラン名のどちらでも検索できるようにする。
    // まずタグとして完全一致検索を試み、見つからなければ(404)クラン名の部分一致検索にフォールバックする。
    @GetMapping("/clan")
    public String clan(@RequestParam(required = false) String tag, Model model, Locale locale) {
        if (tag == null || tag.isBlank()) {
            return "redirect:/";
        }
        model.addAttribute("useJapaneseRoleNames", "ja".equalsIgnoreCase(locale.getLanguage()));
        try {
            ClanResponse clan = clashRoyaleApiClient.getClan(tag);
            model.addAttribute("clan", clan);
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
}
