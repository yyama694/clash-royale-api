package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.config.PlayerIndexProperties;
import com.example.clashroyaleapi.domain.CardUsage;
import com.example.clashroyaleapi.domain.TopDecks;
import com.example.clashroyaleapi.store.TopDecksFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * 最新の集計結果(世界トップのデッキと、そこから求めたカード使用率)を持つ。集計はTopDeckCollectorが行い、ここに渡す。
 * 画面からは読むだけなので、差し替えは参照の付け替え1回で済ませる。
 */
@Service
public class CardUsageService {

    private static final Logger log = LoggerFactory.getLogger(CardUsageService.class);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss").withZone(ZoneOffset.UTC);

    private final TopDecksFile file;
    private final Path historyDir;
    private volatile TopDecks topDecks;
    private volatile CardUsage current;

    @Autowired
    public CardUsageService(PlayerIndexProperties properties) {
        // プレイヤー名の索引と同じく、jarを差し替えても消えない場所に置く。
        this(new TopDecksFile(Path.of(properties.dir()).resolve("card-usage").resolve("top-decks.tsv")),
                Path.of(properties.dir()).resolve("card-usage").resolve("history"));
    }

    CardUsageService(TopDecksFile file, Path historyDir) {
        this.file = file;
        this.historyDir = historyDir;
        try {
            file.read().ifPresent(this::replace);
        } catch (IOException e) {
            log.warn("could not read the last card usage snapshot (will be collected again): {}", e.toString());
        }
    }

    /** まだ一度も集計していなければ空。 */
    public Optional<CardUsage> current() {
        return Optional.ofNullable(current);
    }

    /** まだ一度も集計していなければ空。 */
    public Optional<TopDecks> topDecks() {
        return Optional.ofNullable(topDecks);
    }

    public void publish(TopDecks topDecks) {
        replace(topDecks);
        try {
            file.write(topDecks);
        } catch (IOException e) {
            // 画面には新しい集計が出ているので、保存の失敗は次回の再起動まで影響しない。
            log.warn("could not save the card usage snapshot: {}", e.toString());
        }
        // 公式APIは直近約30戦しか返さず、過去のデッキは後から集め直せない。使用率の推移を出せるよう、毎回の集計を残す。
        try {
            historyFile(topDecks).write(topDecks);
        } catch (IOException e) {
            log.warn("could not save the card usage history: {}", e.toString());
        }
    }

    /** 集計ごとに history/<日付>/<時分秒>.tsv(UTC)。中身は top-decks.tsv と同じ形式。 */
    private TopDecksFile historyFile(TopDecks topDecks) {
        Instant collectedAt = topDecks.collectedAt();
        return new TopDecksFile(historyDir.resolve(DAY.format(collectedAt)).resolve(TIME.format(collectedAt) + ".tsv"));
    }

    private void replace(TopDecks latest) {
        CardUsage usage = CardUsage.of(latest);
        topDecks = latest;
        current = usage;
    }
}
