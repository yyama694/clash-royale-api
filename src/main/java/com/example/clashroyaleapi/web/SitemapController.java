package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.service.CardService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Stream;

/**
 * 検索エンジン向けのsitemap.xmlとrobots.txt、AI検索向けのllms.txt。ドメインは過去に何度か変わっているため、
 * 静的ファイルに焼き込まずアクセスされたホストから組み立てる(siteBaseUrlと同じ考え方)。
 * お気に入り画面はCookieで内容が閲覧者ごとに変わり、検索結果として案内する価値が無いため対象外にする。
 */
@RestController
public class SitemapController {

    private static final List<String> STATIC_PATHS = List.of("/", "/cards", "/ranking", "/ranking/players");

    private final CardService cardService;
    private final GlobalModelAttributes modelAttributes;
    private final LabelResolver labels;

    public SitemapController(CardService cardService, GlobalModelAttributes modelAttributes, LabelResolver labels) {
        this.cardService = cardService;
        this.modelAttributes = modelAttributes;
        this.labels = labels;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap(HttpServletRequest request) {
        String base = modelAttributes.siteBaseUrl(request);
        List<String> languageCodes = modelAttributes.languageCodes();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\" "
                + "xmlns:xhtml=\"http://www.w3.org/1999/xhtml\">\n");
        Stream.concat(STATIC_PATHS.stream(), cardPaths())
                .forEach(path -> appendUrl(xml, base, path, languageCodes));
        xml.append("</urlset>\n");
        return xml.toString();
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots(HttpServletRequest request) {
        String base = modelAttributes.siteBaseUrl(request);
        return """
                User-agent: *
                Disallow: /favorites

                Sitemap: %s/sitemap.xml
                """.formatted(base);
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
                > 9 million players built by continuously crawling clans - so even players nobody has
                > ever looked up are searchable.

                ## What you can look up

                - Player search by name or tag: current deck, average elixir, 4-card cycle,
                  win/loss streaks, Ranked league placement, battle history, card collection
                - Clan search by name or tag: member list with role, trophies, donations, last seen
                - Rankings: top 1000 players and top 1000 clans, global or by country
                - Cards: full card list and per-card detail pages, including Evolution artwork

                ## Pages

                - Player and clan search: %s/
                - Player rankings: %s/ranking/players
                - Clan rankings: %s/ranking
                - Card list: %s/cards

                ## Notes

                - Data comes from the official Supercell Clash Royale API.
                - Available languages: %s
                - Not affiliated with or endorsed by Supercell.
                """.formatted(siteName, base, base, base, base, languages);
    }

    private Stream<String> cardPaths() {
        return cardService.catalog().stream()
                .flatMap(group -> group.cards().stream())
                .map(card -> "/card/" + card.id());
    }

    private void appendUrl(StringBuilder xml, String base, String path, List<String> languageCodes) {
        String loc = base + path;
        xml.append("  <url>\n");
        xml.append("    <loc>").append(loc).append("</loc>\n");
        languageCodes.forEach(code -> xml.append("    <xhtml:link rel=\"alternate\" hreflang=\"").append(code)
                .append("\" href=\"").append(loc).append("?lang=").append(code).append("\"/>\n"));
        xml.append("    <xhtml:link rel=\"alternate\" hreflang=\"x-default\" href=\"").append(loc).append("\"/>\n");
        xml.append("  </url>\n");
    }
}
