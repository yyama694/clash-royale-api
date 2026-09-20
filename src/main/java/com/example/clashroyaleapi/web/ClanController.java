package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.domain.ClanSearchResult;
import com.example.clashroyaleapi.domain.GameText;
import com.example.clashroyaleapi.domain.MemberSortKey;
import com.example.clashroyaleapi.domain.SortDirection;
import com.example.clashroyaleapi.service.ClanService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Controller
@RequestMapping("/clan")
public class ClanController {

    private final ClanService clanService;
    private final ViewMapper viewMapper;
    private final FavoriteCookies favoriteCookies;

    public ClanController(ClanService clanService, ViewMapper viewMapper, FavoriteCookies favoriteCookies) {
        this.clanService = clanService;
        this.viewMapper = viewMapper;
        this.favoriteCookies = favoriteCookies;
    }

    /** クランタグ・クラン名のどちらでも受け取る検索の受け口。 */
    @GetMapping("/search")
    public String search(@RequestParam(required = false) String q, Model model) {
        if (q == null || q.isBlank()) {
            return "redirect:/";
        }
        return switch (clanService.search(q)) {
            case ClanSearchResult.Found found -> "redirect:/clan/"
                    + UriUtils.encodePathSegment(Tags.toPathSegment(found.clan().tag()), StandardCharsets.UTF_8);
            case ClanSearchResult.Candidates candidates -> {
                model.addAttribute("query", q);
                model.addAttribute("candidates", viewMapper.toClanSummaries(candidates.clans()));
                model.addAttribute("total", candidates.total());
                yield "clan-search";
            }
            case ClanSearchResult.NotFound notFound -> {
                model.addAttribute("query", notFound.query());
                yield "clan-search";
            }
        };
    }

    @GetMapping("/{tag}")
    public String clan(@PathVariable String tag,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String favoriteLimit,
            Model model, Locale locale, HttpServletRequest request, HttpServletResponse response) {
        ClanResponse clan = clanService.findClan(tag);
        MemberSortKey sortKey = MemberSortKey.from(sortBy).orElse(null);
        SortDirection direction = SortDirection.from(sortDir);
        String clanName = GameText.stripFormatting(clan.name());
        String clanPathTag = Tags.toPathSegment(clan.tag());

        model.addAttribute("clan", clan);
        model.addAttribute("clanName", clanName);
        model.addAttribute("clanPathTag", clanPathTag);
        model.addAttribute("favorite",
                favoriteCookies.refreshName(request, response, FavoriteKind.CLAN, clanPathTag, clanName)
                        .contains(clanPathTag));
        model.addAttribute("favoriteLimit", favoriteLimit != null);
        model.addAttribute("members",
                viewMapper.toMembers(clanService.sortMembers(clan.memberList(), sortKey, direction), locale));
        model.addAttribute("sortBy", sortKey == null ? null : sortKey.code());
        model.addAttribute("sortDir", direction.code());
        return "clan";
    }
}
