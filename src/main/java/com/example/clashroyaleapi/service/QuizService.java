package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.domain.DailyQuiz;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

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
    private final Clock clock;

    @Autowired
    public QuizService(ClashRoyaleApiClient apiClient) {
        this(apiClient, Clock.systemUTC());
    }

    QuizService(ClashRoyaleApiClient apiClient, Clock clock) {
        this.apiClient = apiClient;
        this.clock = clock;
    }

    public DailyQuiz today() {
        List<DailyQuiz.Card> cards = apiClient.getCards().items().stream()
                .filter(card -> card.elixirCost() != null)
                .map(card -> new DailyQuiz.Card(card.id(), card.elixirCost()))
                .toList();
        return DailyQuiz.of(LocalDate.now(clock.withZone(ZoneOffset.UTC)), FIRST_DAY, cards);
    }
}
