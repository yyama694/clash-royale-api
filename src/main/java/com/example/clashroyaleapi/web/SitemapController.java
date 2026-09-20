package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.service.CardService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Stream;

/**
 * 検索エンジン向けのsitemap.xmlとrobots.txt。ドメインは過去に何度か変わっているため、
 * 静的ファイルに焼き込まずアクセスされたホストから組み立てる(siteBaseUrlと同じ考え方)。
 * お気に入り画面はCookieで内容が閲覧者ごとに変わり、検索結果として案内する価値が無いため対象外にする。
 */
@RestController
public class SitemapController {

    private static final List<String> STATIC_PATHS = List.of("/", "/cards", "/ranking", "/ranking/players");

    private final CardService cardService;
    private final GlobalModelAttributes modelAttributes;

    public SitemapController(CardService cardService, GlobalModelAttributes modelAttributes) {
        this.cardService = cardService;
        this.modelAttributes = modelAttributes;
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
