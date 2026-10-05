package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.dto.ClanRankingResponse;
import com.example.clashroyaleapi.service.LocationService;
import com.example.clashroyaleapi.service.PlayerRanking;
import com.example.clashroyaleapi.service.RankingService;
import com.example.clashroyaleapi.web.view.ClanRankingRowView;

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
        RankingScope.Scope scope = rankingScope.resolve(country, request, response, model, locale);

        // 1000件×2タブを最初から描くとHTMLが大きくなるため、個人ランキング画面と同じく
        // 最初は開いているタブだけを描き、もう一方は切り替えたときに下の table で取りに来る。
        model.addAttribute("globalRanking", scope.localTabActive() ? List.of()
                : toClanRankingRows(RankingService.GLOBAL_LOCATION_ID, locale));
        model.addAttribute("localRanking", scope.localTabActive()
                ? scope.country().map(c -> toClanRankingRows(c.locationId(), locale)).orElseGet(List::of)
                : List.of());
        // 注記の「上位n件」を文言に直書きすると定数を変えたときにずれるため、件数も渡す。
        model.addAttribute("clanRankingSize", RankingService.CLAN_RANKING_SIZE);
        model.addAttribute("warTrophiesRankLimit", RankingService.WAR_TROPHIES_RANK_LIMIT);
        return "ranking";
    }

    /** タブを切り替えたときに、その国(未指定ならグローバル)の表だけを返す。画面のHTMLは返さない。 */
    @GetMapping("/ranking/table")
    public String rankingTable(@RequestParam(required = false) String country, Model model, Locale locale) {
        model.addAttribute("rows", clanLocationIdFor(country).map(id -> toClanRankingRows(id, locale))
                .orElseGet(List::of));
        // 国別タブはすでにその国に絞っているため、グローバルタブだけ「国・地域」列を出す。
        model.addAttribute("showLocation", country == null || country.isBlank());
        return "fragments/layout :: rankingTable(rows=${rows}, showLocation=${showLocation})";
    }

    private Optional<String> clanLocationIdFor(String country) {
        if (country == null || country.isBlank()) {
            return Optional.of(RankingService.GLOBAL_LOCATION_ID);
        }
        return locationService.byCountryCode(locationService.countries(), country).map(c -> c.locationId());
    }

    /** クランスコアが上限で並ぶ上位クランを見分けられるよう、上位だけクラン対戦トロフィーを添える。 */
    private List<ClanRankingRowView> toClanRankingRows(String locationId, Locale locale) {
        List<ClanRankingResponse.RankedClan> clans = rankingService.topClans(locationId);
        return viewMapper.toClanRankingRows(clans, rankingService.warTrophiesOfTopClans(locationId, clans), locale);
    }

    @GetMapping("/ranking/players")
    public String playerRanking(@RequestParam(required = false) String country, HttpServletRequest request,
            HttpServletResponse response, Model model, Locale locale) {
        RankingScope.Scope scope = rankingScope.resolve(country, request, response, model, locale);

        // 1000人×2タブを最初から描くとHTMLが1.6MBになり、低スペックVMでは表示に数秒かかる。
        // 最初は開いているタブだけを描き、もう一方は切り替えたときに下の table で取りに来る。
        // 描かないタブは null にしておく(テンプレートは開いているタブしか描かない)。
        model.addAttribute("globalRanking", scope.localTabActive() ? null
                : viewMapper.toPlayerRanking(
                        rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID, PLAYER_RANKING_SIZE), locale));
        model.addAttribute("localRanking", scope.localTabActive()
                ? scope.country()
                        .map(c -> viewMapper.toPlayerRanking(
                                rankingService.topPlayers(c.locationId(), PLAYER_RANKING_SIZE), locale))
                        .orElse(null)
                : null);
        model.addAttribute("playerRankingSize", PLAYER_RANKING_SIZE);
        return "player-ranking";
    }

    /** タブを切り替えたときに、その国(未指定ならグローバル)の表だけを返す。画面のHTMLは返さない。 */
    @GetMapping("/ranking/players/table")
    public String playerRankingTable(@RequestParam(required = false) String country, Model model, Locale locale) {
        model.addAttribute("ranking", viewMapper.toPlayerRanking(playerRankingFor(country), locale));
        return "fragments/layout :: playerRankingTable(ranking=${ranking}, detailed=true)";
    }

    private PlayerRanking playerRankingFor(String country) {
        if (country == null || country.isBlank()) {
            return rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID, PLAYER_RANKING_SIZE);
        }
        // 国が一覧に無ければ、グローバルの内容をその国のものとして見せないよう空にする(画面は「取得できません」を出す)。
        return locationService.byCountryCode(locationService.countries(), country)
                .map(c -> rankingService.topPlayers(c.locationId(), PLAYER_RANKING_SIZE))
                .orElseGet(PlayerRanking::unavailable);
    }
}
