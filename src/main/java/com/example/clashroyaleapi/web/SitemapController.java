package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.client.exception.ClashRoyaleApiException;
import com.example.clashroyaleapi.config.IndexNowProperties;
import com.example.clashroyaleapi.domain.Country;
import com.example.clashroyaleapi.service.BalanceService;
import com.example.clashroyaleapi.service.CardService;
import com.example.clashroyaleapi.service.CardUsageService;
import com.example.clashroyaleapi.service.LocationService;
import com.example.clashroyaleapi.service.RankingHistoryService;
import com.example.clashroyaleapi.service.RankingService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 検索エンジン向けのsitemap.xmlとrobots.txt、AI検索向けのllms.txt。ドメインは過去に何度か変わっているため、
 * 静的ファイルに焼き込まずアクセスされたホストから組み立てる(siteBaseUrlと同じ考え方)。
 * お気に入り画面はCookieで内容が閲覧者ごとに変わり、検索結果として案内する価値が無いため対象外にする。
 */
@RestController
public class SitemapController {

    private static final List<String> STATIC_PATHS = List.of("/", "/cards", "/decks", "/decks/popular",
            "/decks/win-rates", "/ranking", "/ranking/players", "/ranking/players/movements", "/ranking/players/seasons",
            "/balance", "/about", "/privacy");

    /**
     * 国別ランキングをsitemapに載せる国。「クラロワ 日本 ランキング」のような国名つきの検索の入口にする(2026-10)。
     * 国ごとに公式APIを呼ぶ画面(クランは最大21回)なので、全約200か国ではなく、対応言語の国とプレイヤーの多い国に絞る
     * (国の数は2026-10-17にユーザーが決定)。
     */
    static final List<String> RANKING_COUNTRIES = List.of("JP", "US", "GB", "CA", "AU", "DE", "AT", "FR", "IT", "ES",
            "PT", "NL", "PL", "RU", "UA", "TR", "KR", "BR", "MX", "AR", "CO", "CL", "PE", "ID", "PH");

    private final CardService cardService;
    private final BalanceService balanceService;
    private final CardUsageService cardUsageService;
    private final RankingHistoryService rankingHistoryService;
    private final RankingService rankingService;
    private final LocationService locationService;
    private final GlobalModelAttributes modelAttributes;
    private final LabelResolver labels;
    private final String indexNowKey;

    public SitemapController(CardService cardService, BalanceService balanceService, CardUsageService cardUsageService,
            RankingHistoryService rankingHistoryService, RankingService rankingService,
            LocationService locationService, GlobalModelAttributes modelAttributes, LabelResolver labels,
            IndexNowProperties indexNowProperties) {
        this.cardService = cardService;
        this.balanceService = balanceService;
        this.cardUsageService = cardUsageService;
        this.rankingHistoryService = rankingHistoryService;
        this.rankingService = rankingService;
        this.locationService = locationService;
        this.modelAttributes = modelAttributes;
        this.labels = labels;
        this.indexNowKey = indexNowProperties.key();
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap(HttpServletRequest request) {
        String base = modelAttributes.siteBaseUrl(request);
        List<String> languageCodes = modelAttributes.languageCodes();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\" "
                + "xmlns:xhtml=\"http://www.w3.org/1999/xhtml\">\n");
        Stream.of(STATIC_PATHS.stream(), cardUsageRankingPaths(), cardPaths(), cardDeckPaths(), countryRankingPaths(),
                        movementDayPaths(), seasonPaths(), balancePaths())
                .flatMap(paths -> paths)
                .forEach(path -> appendUrl(xml, base, path, languageCodes));
        xml.append("</urlset>\n");
        return xml.toString();
    }

    /**
     * 世界の上位1000人(15分ごとの記録の最新)のプレイヤー情報画面。プレイヤー名での検索は、有名な人や上位の人の
     * 名前に集まるため。公式APIは呼ばない。各言語のURLは画面のhreflangで伝わるので、ここには言語の無いURLだけを
     * 載せる(全言語を並べると1000人で十数MBになる)。lastmodは記録した時刻(上位の人の画面は対戦のたびに変わる)。
     * 他の画面と分けておくと、Search Consoleでこの1000人がどれだけ登録されたかを別に見られる。
     */
    @GetMapping(value = "/sitemap-players.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String playerSitemap(HttpServletRequest request) {
        String base = modelAttributes.siteBaseUrl(request);
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        rankingHistoryService.latestSnapshot().ifPresent(snapshot -> {
            String lastmod = snapshot.takenAt().truncatedTo(ChronoUnit.SECONDS).toString();
            snapshot.entries().forEach(entry -> xml.append("  <url>\n")
                    .append("    <loc>").append(xmlEscape(base + "/player/" + Tags.toPathSegment(entry.tag())))
                    .append("</loc>\n")
                    .append("    <lastmod>").append(lastmod).append("</lastmod>\n")
                    .append("  </url>\n"));
        });
        xml.append("</urlset>\n");
        return xml.toString();
    }

    /**
     * 世界のクランランキングの上位1000クランのクラン情報画面。検索から来た人の着地にはクランもあるため(2026-10)。
     * 公式APIはランキングの1回だけ(ランキング画面と同じキャッシュ)。取れなければ空のsitemapを返す。
     * プレイヤーと同じく言語の無いURLだけを載せる。ランキングに更新時刻が無いのでlastmodは付けない。
     */
    @GetMapping(value = "/sitemap-clans.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String clanSitemap(HttpServletRequest request) {
        String base = modelAttributes.siteBaseUrl(request);
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        rankingService.topClans(RankingService.GLOBAL_LOCATION_ID).forEach(clan -> xml.append("  <url>\n")
                .append("    <loc>").append(xmlEscape(base + "/clan/" + Tags.toPathSegment(clan.tag())))
                .append("</loc>\n")
                .append("  </url>\n"));
        xml.append("</urlset>\n");
        return xml.toString();
    }

    /**
     * IndexNowの鍵のファイル。検索エンジンが、送られてきたURLがこのサイトの持ち主からのものかを、ここを読んで確かめる
     * (IndexNowSubmitter)。鍵は公開する前提の目印なので、送るのを止めているときも配信してよい。
     */
    @GetMapping(value = "/${indexnow.key}.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String indexNowKey() {
        return indexNowKey;
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots(HttpServletRequest request) {
        String base = modelAttributes.siteBaseUrl(request);
        return """
                User-agent: *
                Disallow: /favorites
                Disallow: /beacon

                Sitemap: %s/sitemap.xml
                Sitemap: %s/sitemap-players.xml
                Sitemap: %s/sitemap-clans.xml
                """.formatted(base, base, base);
    }

    /**
     * AI検索(ChatGPT・Claude・Perplexity等)向けの要約。当サイトのトラフィックの大半はAIクローラーで、
     * 「クラロワのプレイヤーを調べられるサイト」を聞かれたときに拾われることを狙う。
     * サイト名はmessagesを正とし、ここに二重に持たない(改名時の直し漏れを防ぐため)。
     */
    @GetMapping(value = "/llms.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String llms(HttpServletRequest request) {
        String base = modelAttributes.siteBaseUrl(request);
        String siteName = labels.message("app.name", SupportedLanguages.INTERNATIONAL);
        String languages = String.join(", ", modelAttributes.languageCodes());
        return """
                # %s

                > A fan-made Clash Royale player and clan lookup site. Unlike tools that only accept
                > player tags, this site finds players by their in-game name, using an index of over
                > 50 million players built by continuously crawling clans - so even players nobody has
                > ever looked up are searchable.

                ## What you can look up

                - Player search by name or tag: current deck, average elixir, 4-card cycle,
                  win/loss streaks, Rank Battle leaderboard position (top players), battle history,
                  card collection
                - Clan search by name or tag: member list with role, trophies, donations, last seen
                - Rankings: top 1000 players and top 1000 clans, global or by country
                  (e.g. %s/ranking/players?country=JP, %s/ranking?country=BR)
                - Cards: full card list and per-card detail pages, including Evolution artwork

                ## Pages

                - Player and clan search: %s/
                - Player rankings: %s/ranking/players
                - Top players' decks (latest Rank Battle deck of the global top players, updated daily; filter by up to
                  8 cards to show only decks with all of them, e.g. /decks?card=26000021&card=28000014): %s/decks
                - Popular decks (decks with exactly the same 8 cards used by two or more of the global top players,
                  most-used first; filterable by card like the page above): %s/decks/popular
                - Decks with the highest win rates (win rates in this season's Rank Battles between the global top
                  players, counted from both sides of each battle; only decks with enough battles and players,
                  updated daily): %s/decks/win-rates
                - Rank Battle world ranking report (who is #1 and since when, #1 changes, top 10 and biggest
                  rating gains of the last 24 hours; recorded every 15 minutes, with a page for each day): %s/ranking/players/movements
                - Final Rank Battle standings of each finished season (global top 1000, since October 2022):
                  %s/ranking/players/seasons
                - Clan rankings: %s/ranking
                - Card list: %s/cards
                - Card usage rankings (share of the global top players using each card, updated daily): %s/cards?sort=usage
                - Balance changes (official buffs and nerfs, with each card's usage among the global top players
                  before the change, 1, 3 and 7 days after, and now; with a page for each change): %s/balance

                ## Notes

                - Data comes from the official Supercell Clash Royale API.
                - Available languages: %s
                - Not affiliated with or endorsed by Supercell.
                - About this site and contact: %s/about
                """.formatted(siteName, base, base, base, base, base, base, base, base, base, base, base, base, base, languages,
                base);
    }

    private Stream<String> cardPaths() {
        return catalogCardIds().map(id -> "/card/" + id);
    }

    /**
     * カード別のデッキ画面。「ホグライダー デッキ」のようなカード名での検索の入口にする。
     * トップ層で誰も使っていないカードは中身が空なので載せない(画面側もnoindexにしている)。
     * 集計がまだ無いとき(デプロイ直後の1時間ほど)は、どのカードが空か分からないので1件も載せない。
     */
    private Stream<String> cardDeckPaths() {
        return cardUsageService.current().stream()
                .flatMap(usage -> catalogCardIds().filter(id -> usage.usageOf(id).users() > 0))
                .map(id -> "/decks?card=" + id);
    }

    /** カードの使用率ランキング(カード一覧の使用率順)。集計がまだ無いときは画面がレアリティ別と同じになるので載せない。 */
    private Stream<String> cardUsageRankingPaths() {
        return cardUsageService.current().stream().map(usage -> "/cards?sort=usage");
    }

    /** 国別の個人ランキングとクランランキング。国の一覧が取れなければ(公式APIの障害)、そのときは載せない。 */
    private Stream<String> countryRankingPaths() {
        List<Country> countries = locationService.countries();
        return RANKING_COUNTRIES.stream()
                .filter(code -> Country.find(countries, code).isPresent())
                .flatMap(code -> Stream.of("/ranking/players?country=" + code, "/ranking?country=" + code));
    }

    /** 日ごとのランキングの動き。内容が固定されたページなので、記録のある終わった日をすべて載せる。 */
    /** バランス調整ごとの画面。 */
    private Stream<String> balancePaths() {
        return balanceService.updates().stream().map(update -> "/balance/" + update.date());
    }

    private Stream<String> movementDayPaths() {
        return rankingHistoryService.archiveDays().stream().map(day -> "/ranking/players/movements/" + day);
    }

    /** シーズンごとの最終順位。内容が固定されたページ。一覧が取れなければ、そのときは載せない(sitemap全体は返す)。 */
    private Stream<String> seasonPaths() {
        try {
            return rankingService.finishedSeasons().stream().map(season -> "/ranking/players/seasons/" + season);
        } catch (ClashRoyaleApiException e) {
            return Stream.empty();
        }
    }

    private Stream<Integer> catalogCardIds() {
        return cardService.catalog().stream()
                .flatMap(group -> group.cards().stream())
                .map(CardsResponse.Card::id);
    }

    /**
     * 1つの画面について、各言語のURLとx-defaultのURLをそれぞれ<url>として載せ、どの<url>にも全言語の対応を並べる
     * (Googleの説明どおりの形)。言語のURLを<xhtml:link>の中にだけ書くと、検索エンジンが巡回する対象として
     * 扱われる保証が無い。
     */
    private void appendUrl(StringBuilder xml, String base, String path, List<String> languageCodes) {
        String loc = base + path;
        String langSeparator = path.contains("?") ? "&" : "?";
        Map<String, String> alternates = new LinkedHashMap<>();
        languageCodes.forEach(code -> alternates.put(code, loc + langSeparator + "lang=" + code));
        alternates.put("x-default", loc);
        for (String url : alternates.values()) {
            xml.append("  <url>\n");
            xml.append("    <loc>").append(xmlEscape(url)).append("</loc>\n");
            alternates.forEach((hreflang, href) -> xml.append("    <xhtml:link rel=\"alternate\" hreflang=\"")
                    .append(hreflang).append("\" href=\"").append(xmlEscape(href)).append("\"/>\n"));
            xml.append("  </url>\n");
        }
    }

    // クエリ付きのURLの「&」は、XMLではそのままだと不正になる。
    private static String xmlEscape(String url) {
        return url.replace("&", "&amp;");
    }
}
