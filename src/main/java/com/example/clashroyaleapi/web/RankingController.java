package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.dto.ClanRankingResponse;
import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;
import com.example.clashroyaleapi.domain.PageSlice;
import com.example.clashroyaleapi.service.LocationService;
import com.example.clashroyaleapi.service.RankingService;
import com.example.clashroyaleapi.web.view.ClanRankingRowView;
import com.example.clashroyaleapi.web.view.PlayerRankingRowView;

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

    // 1000件を1ページに並べるとスマホ幅で14万pxを超えるため、100件ずつに分ける。
    static final int RANKING_PAGE_SIZE = 100;

    private static final String CLAN_RANKING_PATH = "/ranking";
    private static final String PLAYER_RANKING_PATH = "/ranking/players";

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

    /**
     * 最初は開いているタブだけを描き、もう一方は切り替えたときに下の table で取りに来る(1ページ目)。
     * page は開いているタブのページ。国を選び直した直後(country あり)は国別タブ、それ以外はグローバルタブが開く。
     */
    @GetMapping(CLAN_RANKING_PATH)
    public String ranking(@RequestParam(required = false) String country,
            @RequestParam(required = false, defaultValue = "1") int page, HttpServletRequest request,
            HttpServletResponse response, Model model, Locale locale) {
        RankingScope.Scope scope = rankingScope.resolve(country, request, response, model, locale);
        model.addAttribute("globalRanking", List.of());
        model.addAttribute("localRanking", List.of());
        if (scope.localTabActive()) {
            scope.country().ifPresent(c -> addClanPage(model, "local",
                    toClanRankingRows(c.locationId(), locale), c.countryCode(), page));
        } else {
            addClanPage(model, "global", toClanRankingRows(RankingService.GLOBAL_LOCATION_ID, locale), null, page);
        }
        // 注記の「上位n件」を文言に直書きすると定数を変えたときにずれるため、件数も渡す。
        model.addAttribute("clanRankingSize", RankingService.CLAN_RANKING_SIZE);
        model.addAttribute("warTrophiesRankLimit", RankingService.WAR_TROPHIES_RANK_LIMIT);
        return "ranking";
    }

    /** タブを切り替えたときに、その国(未指定ならグローバル)の表の1ページ目だけを返す。画面のHTMLは返さない。 */
    @GetMapping(CLAN_RANKING_PATH + "/table")
    public String rankingTable(@RequestParam(required = false) String country, Model model, Locale locale) {
        boolean global = country == null || country.isBlank();
        List<ClanRankingRowView> rows = clanLocationIdFor(country).map(id -> toClanRankingRows(id, locale))
                .orElseGet(List::of);
        addClanPage(model, "table", rows, global ? null : country.strip(), 1);
        // 国別タブはすでにその国に絞っているため、グローバルタブだけ「国・地域」列を出す。
        model.addAttribute("showLocation", global);
        return "fragments/layout :: rankingTable(rows=${tableRanking}, showLocation=${showLocation}, pager=${tablePager})";
    }

    private void addClanPage(Model model, String prefix, List<ClanRankingRowView> rows, String countryCode, int page) {
        PageSlice<ClanRankingRowView> slice = PageSlice.of(rows, page, RANKING_PAGE_SIZE);
        model.addAttribute(prefix + "Ranking", slice.items());
        model.addAttribute(prefix + "Pager", viewMapper.toRankingPager(CLAN_RANKING_PATH, countryCode, slice));
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

    /** 1000人×2タブを最初から描くとHTMLが1.6MBになり、低スペックVMでは表示に数秒かかるため、クランランキングと同じく分けて描く。 */
    @GetMapping(PLAYER_RANKING_PATH)
    public String playerRanking(@RequestParam(required = false) String country,
            @RequestParam(required = false, defaultValue = "1") int page, HttpServletRequest request,
            HttpServletResponse response, Model model, Locale locale) {
        RankingScope.Scope scope = rankingScope.resolve(country, request, response, model, locale);
        model.addAttribute("globalRanking", List.of());
        model.addAttribute("localRanking", List.of());
        if (scope.localTabActive()) {
            scope.country().ifPresent(c -> addPlayerPage(model, "local",
                    rankingService.topPlayers(c.locationId(), PLAYER_RANKING_SIZE), c.countryCode(), page));
        } else {
            addPlayerPage(model, "global",
                    rankingService.topPlayers(RankingService.GLOBAL_LOCATION_ID, PLAYER_RANKING_SIZE), null, page);
        }
        model.addAttribute("playerRankingSize", PLAYER_RANKING_SIZE);
        return "player-ranking";
    }

    /** タブを切り替えたときに、その国(未指定ならグローバル)の表の1ページ目だけを返す。画面のHTMLは返さない。 */
    @GetMapping(PLAYER_RANKING_PATH + "/table")
    public String playerRankingTable(@RequestParam(required = false) String country, Model model) {
        boolean global = country == null || country.isBlank();
        addPlayerPage(model, "table", rankingRowsFor(country), global ? null : country.strip(), 1);
        return "fragments/layout :: playerRankingTable(rows=${tableRanking}, detailed=true, pager=${tablePager})";
    }

    private void addPlayerPage(Model model, String prefix, List<PlayerRankingResponse.RankedPlayer> players,
            String countryCode, int page) {
        PageSlice<PlayerRankingRowView> slice = PageSlice.of(viewMapper.toPlayerRankingRows(players), page,
                RANKING_PAGE_SIZE);
        model.addAttribute(prefix + "Ranking", slice.items());
        model.addAttribute(prefix + "Pager", viewMapper.toRankingPager(PLAYER_RANKING_PATH, countryCode, slice));
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
