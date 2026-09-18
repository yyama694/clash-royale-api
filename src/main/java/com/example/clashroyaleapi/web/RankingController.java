package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;
import com.example.clashroyaleapi.domain.Country;
import com.example.clashroyaleapi.service.LocationService;
import com.example.clashroyaleapi.service.RankingService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Controller
public class RankingController {

    // トップページ(概要のみ)より多く見せる画面なので、取得できる最大件数をそのまま出す。
    private static final int PLAYER_RANKING_SIZE = RankingService.MAX_PLAYER_RANKING_SIZE;

    private final RankingService rankingService;
    private final RankingScope rankingScope;
    private final LocationService locationService;
    private final ViewMapper viewMapper;

    public RankingController(RankingService rankingService, RankingScope rankingScope,
            LocationService locationService, ViewMapper viewMapper) {
        this.rankingService = rankingService;
        this.rankingScope = rankingScope;
        this.locationService = locationService;
        this.viewMapper = viewMapper;
    }

    @GetMapping("/ranking")
    public String ranking(@RequestParam(required = false) String country, HttpServletRequest request,
            HttpServletResponse response, Model model, Locale locale) {
        Optional<Country> selected = rankingScope.resolve(country, request, response, model, locale).country();

        // タブ切り替えはブラウザ側で行うため、両方のランキングをここで取得しておく(切り替え時に再通信しない)。
        model.addAttribute("globalRanking",
                viewMapper.toClanRankingRows(rankingService.topClans(RankingService.GLOBAL_LOCATION_ID), locale));
        model.addAttribute("localRanking", selected
                .map(c -> viewMapper.toClanRankingRows(rankingService.topClans(c.locationId()), locale))
                .orElseGet(List::of));
        // 注記の「上位n件」を文言に直書きすると定数を変えたときにずれるため、件数も渡す。
        model.addAttribute("clanRankingSize", RankingService.CLAN_RANKING_SIZE);
        return "ranking";
    }

    @GetMapping("/ranking/players")
    public String playerRanking(@RequestParam(required = false) String country, HttpServletRequest request,
            HttpServletResponse response, Model model, Locale locale) {
        RankingScope.Scope scope = rankingScope.resolve(country, request, response, model, locale);

        // 1000人×2タブを最初から描くとHTMLが1.6MBになり、低スペックVMでは表示に数秒かかる。
        // 最初は開いているタブだけを描き、もう一方は切り替えたときに下の table で取りに来る。
        model.addAttribute("globalRanking", scope.localTabActive() ? List.of()
                : viewMapper.toPlayerRankingRows(
                        rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID, PLAYER_RANKING_SIZE)));
        model.addAttribute("localRanking", scope.localTabActive()
                ? scope.country()
                        .map(c -> viewMapper.toPlayerRankingRows(
                                rankingService.topPlayers(c.locationId(), PLAYER_RANKING_SIZE)))
                        .orElseGet(List::of)
                : List.of());
        model.addAttribute("playerRankingSize", PLAYER_RANKING_SIZE);
        return "player-ranking";
    }

    /** タブを切り替えたときに、その国(未指定ならグローバル)の表だけを返す。画面のHTMLは返さない。 */
    @GetMapping("/ranking/players/table")
    public String playerRankingTable(@RequestParam(required = false) String country, Model model) {
        model.addAttribute("rows", viewMapper.toPlayerRankingRows(rankingRowsFor(country)));
        return "fragments/layout :: playerRankingTable(rows=${rows}, detailed=true)";
    }

    private List<PlayerRankingResponse.RankedPlayer> rankingRowsFor(String country) {
        if (country == null || country.isBlank()) {
            return rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID, PLAYER_RANKING_SIZE);
        }
        // 国が一覧に無ければ、グローバルの内容をその国のものとして見せないよう空にする(画面は「取得できません」を出す)。
        return locationService.byCountryCode(locationService.countries(), country)
                .map(c -> rankingService.topPlayers(c.locationId(), PLAYER_RANKING_SIZE))
                .orElseGet(List::of);
    }
}
