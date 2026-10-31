package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.domain.CardStats;
import com.example.clashroyaleapi.domain.DailyQuiz;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;

/**
 * クラロワクイズの今日の問題。1日はUTCで区切る(日本時間 9:00に替わる)。世界で同じ瞬間に替わり、「第〇回」が全員でそろうため。
 * 出題するのは公式APIのカード一覧(キャッシュ済み)のうちエリクサーが決まっているカードで、鏡とタワーユニットは入らない。
 * 公式APIのエリクサーは、2026-10-27に全122枚を照らして一致を確かめた(新しいカードと値に確信の持てないカードは、
 * 10/25にゲーム内で撮ったカード情報の画面で確かめた)。
 */
@Service
public class QuizService {

    /** 第1回の日。隠し機能として本番に入れた日。公開するときに数え直すかは、そのとき決める(設計_クイズ.md)。 */
    static final LocalDate FIRST_DAY = LocalDate.of(2026, 10, 27);

    private final ClashRoyaleApiClient apiClient;
    private final CardStatsService cardStatsService;
    private final Clock clock;

    @Autowired
    public QuizService(ClashRoyaleApiClient apiClient, CardStatsService cardStatsService) {
        this(apiClient, cardStatsService, Clock.systemUTC());
    }

    QuizService(ClashRoyaleApiClient apiClient, CardStatsService cardStatsService, Clock clock) {
        this.apiClient = apiClient;
        this.cardStatsService = cardStatsService;
        this.clock = clock;
    }

    /**
     * 地上に出すと別のコストになるカード(スピリットエンプレス)は、地上の形も別のカードとして出す。
     * 地上のコストは公式APIに無いので、ゲームの画面から読み取ったステータスの「エリクサー（地上）」を使う。
     * ヒットポイントの並べ替えも、ステータスのヒットポイントで出す(地上の形は霊龍の形と同じ値なので出さない)。
     */
    public DailyQuiz today() {
        CardStats stats = cardStatsService.stats();
        List<DailyQuiz.Card> cards = apiClient.getCards().items().stream()
                .filter(card -> card.elixirCost() != null)
                .flatMap(card -> {
                    DailyQuiz.Card normal = new DailyQuiz.Card(card.id(), false, card.elixirCost(),
                            hpOrderHealth(stats, card.name()));
                    OptionalInt ground = stats.groundElixir(card.name());
                    return ground.isPresent()
                            ? Stream.of(normal, new DailyQuiz.Card(card.id(), true, ground.getAsInt()))
                            : Stream.of(normal);
                })
                .toList();
        return DailyQuiz.of(LocalDate.now(clock.withZone(ZoneOffset.UTC)), FIRST_DAY, cards);
    }

    /**
     * 何体も出るカードのヒットポイントは1体あたり、シールドのあるカードはシールドを含まない値なので、
     * 全体の値と勘違いして間違えないよう並べ替えには出さない(2026-10-31にユーザーが決定)。
     */
    private static Integer hpOrderHealth(CardStats stats, String card) {
        if (stats.has(card, CardStats.COUNT) || stats.has(card, CardStats.SHIELD_HEALTH)) {
            return null;
        }
        OptionalInt health = stats.health(card);
        return health.isPresent() ? health.getAsInt() : null;
    }
}
