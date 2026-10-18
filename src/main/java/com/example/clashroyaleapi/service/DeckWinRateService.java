package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.config.PlayerIndexProperties;
import com.example.clashroyaleapi.domain.DeckWinRates;
import com.example.clashroyaleapi.store.DeckWinRatesFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * 勝率の高いデッキ画面。最新の集計結果を持つ。集計はTopDeckCollectorがデッキ集計と同じ対戦履歴から行い、ここに渡す。
 * 画面からは読むだけなので、差し替えは参照の付け替え1回で済ませる({@link CardUsageService} と同じ)。
 */
@Service
public class DeckWinRateService {

    private static final Logger log = LoggerFactory.getLogger(DeckWinRateService.class);

    private final DeckWinRatesFile file;
    private volatile DeckWinRates current;

    @Autowired
    public DeckWinRateService(PlayerIndexProperties properties) {
        this(new DeckWinRatesFile(Path.of(properties.dir()).resolve("card-usage").resolve("deck-win-rates.tsv")));
    }

    DeckWinRateService(DeckWinRatesFile file) {
        this.file = file;
        try {
            current = file.read().orElse(null);
        } catch (IOException e) {
            log.warn("could not read the last deck win rates (will be collected again): {}", e.toString());
        }
    }

    /** まだ一度も集計していなければ空。 */
    public Optional<DeckWinRates> current() {
        return Optional.ofNullable(current);
    }

    /**
     * @param battles 数えた対戦の数
     * @param decks   画面に出す条件を満たすデッキ(勝率の高い順)
     */
    public record Page(Instant collectedAt, YearMonth finishedSeason, int battles, List<DeckWinRates.DeckRecord> decks) {
    }

    /** まだ一度も集計していなければ空。 */
    public Optional<Page> page() {
        return current().map(winRates -> new Page(winRates.collectedAt(), winRates.finishedSeason(),
                winRates.battles(), winRates.ranked()));
    }

    public void publish(DeckWinRates winRates) {
        current = winRates;
        try {
            file.write(winRates);
        } catch (IOException e) {
            // 画面には新しい集計が出ているので、保存の失敗は次回の再起動まで影響しない。
            log.warn("could not save the deck win rates: {}", e.toString());
        }
    }
}
