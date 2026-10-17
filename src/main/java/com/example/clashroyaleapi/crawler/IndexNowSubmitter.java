package com.example.clashroyaleapi.crawler;

import com.example.clashroyaleapi.client.IndexNowClient;
import com.example.clashroyaleapi.client.exception.ClashRoyaleApiException;
import com.example.clashroyaleapi.config.IndexNowProperties;
import com.example.clashroyaleapi.service.RankingHistoryService;
import com.example.clashroyaleapi.service.RankingService;
import com.example.clashroyaleapi.web.SupportedLanguages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 内容が変わらないまま毎日・毎月増えるページ(日ごとのランキングの動き・シーズンの最終順位)を、IndexNowで検索エンジンに知らせる。
 * 送ったURLはファイルに残し、二度は送らない(内容が変わらないページなので、送り直す意味が無い)。
 * 各言語のURL(?lang=xx)と言語の無いURLを、sitemapと同じくそれぞれ送る。
 */
class IndexNowSubmitter {

    private static final Logger log = LoggerFactory.getLogger(IndexNowSubmitter.class);

    private final IndexNowClient client;
    private final RankingHistoryService historyService;
    private final RankingService rankingService;
    private final IndexNowProperties properties;
    private final Path submittedFile;

    IndexNowSubmitter(IndexNowClient client, RankingHistoryService historyService, RankingService rankingService,
            IndexNowProperties properties, Path submittedFile) {
        this.client = client;
        this.historyService = historyService;
        this.rankingService = rankingService;
        this.properties = properties;
        this.submittedFile = submittedFile;
    }

    void submit() {
        try {
            Set<String> submitted = readSubmitted();
            List<String> fresh = urls().stream().filter(url -> !submitted.contains(url)).toList();
            if (fresh.isEmpty()) {
                return;
            }
            String host = URI.create(properties.siteUrl()).getHost();
            String keyLocation = properties.siteUrl() + "/" + properties.key() + ".txt";
            for (int from = 0; from < fresh.size(); from += IndexNowClient.MAX_URLS) {
                List<String> chunk = fresh.subList(from, Math.min(from + IndexNowClient.MAX_URLS, fresh.size()));
                client.submit(host, properties.key(), keyLocation, chunk);
                recordSubmitted(chunk);
            }
            log.info("indexnow: submitted {} urls", fresh.size());
        } catch (RuntimeException | IOException e) {
            // 送れなかった分は記録していないので、次の回にもう一度送る。
            log.warn("indexnow: submission failed (will retry next time): {}", e.toString());
        }
    }

    List<String> urls() {
        List<String> paths = new ArrayList<>();
        historyService.archiveDays().forEach(day -> paths.add("/ranking/players/movements/" + day));
        try {
            rankingService.finishedSeasons().forEach(season -> paths.add("/ranking/players/seasons/" + season));
        } catch (ClashRoyaleApiException e) {
            // シーズンの一覧が取れなければ、次の回に回す(動きのページは送る)。
            log.warn("indexnow: season list unavailable: {}", e.toString());
        }
        return paths.stream()
                .flatMap(path -> Stream.concat(Stream.of(properties.siteUrl() + path),
                        SupportedLanguages.languageCodes().stream()
                                .map(code -> properties.siteUrl() + path + "?lang=" + code)))
                .toList();
    }

    private Set<String> readSubmitted() throws IOException {
        return Files.exists(submittedFile)
                ? new HashSet<>(Files.readAllLines(submittedFile, StandardCharsets.UTF_8))
                : new HashSet<>();
    }

    private void recordSubmitted(List<String> urls) throws IOException {
        Files.createDirectories(submittedFile.getParent());
        Files.write(submittedFile, urls, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
