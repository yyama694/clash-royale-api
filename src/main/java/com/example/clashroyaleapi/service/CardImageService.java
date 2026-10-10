package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.CardImageClient;
import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.client.exception.ClashRoyaleApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.resource.ResourceUrlProvider;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * 公式APIがURLを返すのに、画像の置き場所にまだ無い(404の)進化・ヒーローの画像を覚えておく。
 * 新しい進化・ヒーローは、画像より先にURLが公式APIに載ることがある(2026-10のエレクトロジャイアントの進化と
 * エレクトロウィザードのヒーロー)。そのまま出すと画像が壊れて見えるので、無い画像は「画像が無い」として扱う。
 * 確かめるのは裏で定期的に行い、画面の表示を待たせない。後から画像が置かれれば、次の確認で出るようになる。
 *
 * 置き場所に無い間は、手元に置いた代わりの画像があればそれを出す(`static/images/cards/`)。
 * 代わりの画像はファンWiki(clashroyale.fandom.com)のカード画像を公式と同じ285x420に収めたもので、
 * 枠の光り方が公式と少し違う。公式に置かれれば公式の画像に戻るので、そうなったら画像と対応表から消してよい。
 */
@Service
public class CardImageService {

    private static final Logger log = LoggerFactory.getLogger(CardImageService.class);

    private static final Map<String, String> SUBSTITUTES = Map.of(
            "https://api-assets.clashroyale.com/cardevolutions/300/_uChZkNHAMq6tPb3v6A49xinOe3CnhjstOhG6OZbPYc.png",
            "/images/cards/electro-giant-evolution.png",
            "https://api-assets.clashroyale.com/cardheroes/300/RsFaHgB3w6vXsTjXdPr3x8l_GbV9TbOUCvIx07prbrQ.png",
            "/images/cards/electro-wizard-hero.png");

    private final ClashRoyaleApiClient apiClient;
    private final CardImageClient imageClient;
    private final Map<String, String> substitutes;
    private final UnaryOperator<String> staticUrl;

    private volatile Set<String> missing = Set.of();

    @Autowired
    public CardImageService(ClashRoyaleApiClient apiClient, CardImageClient imageClient,
            ResourceUrlProvider resourceUrls) {
        // テンプレートは th:src="${...}" でURLをそのまま出すので、内容ハッシュ付きのURLはここで作る
        this(apiClient, imageClient, SUBSTITUTES, path -> {
            String versioned = resourceUrls.getForLookupPath(path);
            return versioned != null ? versioned : path;
        });
    }

    CardImageService(ClashRoyaleApiClient apiClient, CardImageClient imageClient, Map<String, String> substitutes,
            UnaryOperator<String> staticUrl) {
        this.apiClient = apiClient;
        this.imageClient = imageClient;
        this.substitutes = substitutes;
        this.staticUrl = staticUrl;
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    public void refresh() {
        CardsResponse cards;
        try {
            cards = apiClient.getCards();
        } catch (ClashRoyaleApiException e) {
            log.warn("card images: skipped: {}", e.toString());
            return;
        }
        Set<String> found = new HashSet<>();
        for (String url : alternateIconUrls(cards)) {
            try {
                if (!imageClient.exists(url)) {
                    found.add(url);
                }
            } catch (ClashRoyaleApiException e) {
                // 置き場所に繋がらないなら残りも繋がらない。定期実行のスレッドは巡回などと共用なので、
                // 残りのタイムアウトを待たずに打ち切り、前回の結果のままにする。
                log.warn("card images: skipped: {}", e.toString());
                return;
            }
        }
        if (!found.equals(missing)) {
            log.info("card images: missing on the image host: {}", found);
        }
        missing = Set.copyOf(found);
    }

    /** 置き場所に無いと分かっている画像なら、代わりの画像のURLを返す。代わりも無ければ null。 */
    public String usable(String url) {
        if (url == null || !missing.contains(url)) {
            return url;
        }
        String substitute = substitutes.get(url);
        return substitute == null ? null : staticUrl.apply(substitute);
    }

    private static Set<String> alternateIconUrls(CardsResponse cards) {
        Set<String> urls = new HashSet<>();
        Stream.concat(cards.items().stream(), cards.supportItems().stream())
                .map(CardsResponse.Card::iconUrls)
                .filter(Objects::nonNull)
                .forEach(icons -> Stream.of(icons.evolutionMedium(), icons.heroMedium())
                        .filter(Objects::nonNull)
                        .forEach(urls::add));
        return urls;
    }
}
