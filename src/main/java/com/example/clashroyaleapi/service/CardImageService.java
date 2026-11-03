package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.CardImageClient;
import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.client.exception.ClashRoyaleApiException;
import com.example.clashroyaleapi.domain.CardForm;
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
 * 進化・ヒーローの画像を、公式の画像が使えるときだけ公式のURLで、使えないときは手元の代わりの画像で出す。
 * 新しい進化・ヒーローは、公式の画像が遅れて来ることがある。URLだけ先に公式APIに載って置き場所では404のもの
 * (2026-10のエレクトロジャイアントの進化とエレクトロウィザードのヒーロー)と、対戦では使われているのに公式APIに
 * URLすら無いもの(2026-11のジャイアントの進化)があった。404のURLは裏で定期的に確かめて覚えておき、画面の表示を
 * 待たせない。後から公式に置かれれば、次の確認で公式の画像に戻る。
 *
 * 代わりの画像(`static/images/cards/`)はファンWiki(clashroyale.fandom.com)のカード画像を公式と同じ285x420に
 * 収めたもので、枠の光り方が公式と少し違う。公式の画像が出るようになったら、画像と対応表から消してよい。
 */
@Service
public class CardImageService {

    private static final Logger log = LoggerFactory.getLogger(CardImageService.class);

    static final Map<FormIcon, String> SUBSTITUTES = Map.of(
            new FormIcon("Electro Giant", CardForm.EVOLUTION), "/images/cards/electro-giant-evolution.png",
            new FormIcon("Electro Wizard", CardForm.HERO), "/images/cards/electro-wizard-hero.png",
            new FormIcon("Giant", CardForm.EVOLUTION), "/images/cards/giant-evolution.png");

    private static final Map<String, String> GROUND_ICONS = Map.of(
            "Spirit Empress", "/images/cards/spirit-empress-ground.png");

    private final ClashRoyaleApiClient apiClient;
    private final CardImageClient imageClient;
    private final Map<FormIcon, String> substitutes;
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

    CardImageService(ClashRoyaleApiClient apiClient, CardImageClient imageClient, Map<FormIcon, String> substitutes,
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

    /**
     * 地上に出したときの形(スピリットエンプレス（地上）)のカード画像。ゲームでは別のカードだが公式APIには無いので、
     * ファンWikiのカード画像を公式と同じ285x420に収めて手元に置いた。無いカードは null。
     */
    public String groundIconUrl(String card) {
        String path = GROUND_ICONS.get(card);
        return path == null ? null : staticUrl.apply(path);
    }

    /**
     * カードのその形の画像。公式のURL(officialUrl)が置き場所にあればそれを、無い(URLが無い・置き場所に無いと
     * 分かっている)なら代わりの画像を返す。代わりも無ければ null。
     */
    public String formIconUrl(String card, CardForm form, String officialUrl) {
        if (officialUrl != null && !missing.contains(officialUrl)) {
            return officialUrl;
        }
        String substitute = substitutes.get(new FormIcon(card, form));
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

    /** card は公式APIの英語のカード名。 */
    record FormIcon(String card, CardForm form) {
    }
}
