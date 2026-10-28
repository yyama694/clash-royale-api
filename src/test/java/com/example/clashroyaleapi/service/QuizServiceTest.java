package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.domain.CardStats;
import com.example.clashroyaleapi.domain.DailyQuiz;
import com.example.clashroyaleapi.domain.QuizQuestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuizServiceTest {

    private static final int MIRROR = 99;
    private static final int TOWER_PRINCESS = 100;

    // 地上に出すと3エリクサーになるカード(スピリットエンプレスの役)。公式APIのコストは6
    private static final int EMPRESS = 5;

    private ClashRoyaleApiClient apiClient;
    private CardStatsService cardStatsService;

    @BeforeEach
    void setUp() {
        apiClient = mock(ClashRoyaleApiClient.class);
        List<CardsResponse.Card> items = new ArrayList<>();
        IntStream.range(0, 40).forEach(i -> items.add(card(i, 1 + i % 9)));
        items.add(card(MIRROR, null));
        when(apiClient.getCards()).thenReturn(new CardsResponse(items, List.of(card(TOWER_PRINCESS, null))));
        cardStatsService = new CardStatsService(new CardStats(LocalDate.of(2026, 10, 25), Map.of("Card" + EMPRESS,
                List.of(new CardStats.Row(CardStats.Form.NORMAL, CardStats.GROUND_ELIXIR, "3", "3", null)))));
    }

    @Test
    void 地上に出すと別のコストになるカードは地上の形も別の問題として出す() {
        Set<Boolean> forms = new HashSet<>();
        for (int day = 0; day < 60; day++) {
            DailyQuiz quiz = service(Instant.parse("2026-10-26T12:00:00Z").plusSeconds(day * 86_400L)).today();

            for (QuizQuestion question : quiz.questions()) {
                if (question.cardId() == EMPRESS) {
                    forms.add(question.ground());
                    assertEquals(question.ground() ? 3 : 1 + EMPRESS % 9, question.correctChoice());
                } else {
                    assertFalse(question.ground());
                }
            }
        }
        assertEquals(Set.of(false, true), forms);
    }

    @Test
    void 鏡とタワーユニットは出さない() {
        for (int day = 0; day < 30; day++) {
            DailyQuiz quiz = service(Instant.parse("2026-10-26T12:00:00Z").plusSeconds(day * 86_400L)).today();

            List<Integer> ids = quiz.questions().stream().map(QuizQuestion::cardId).toList();
            assertFalse(ids.contains(MIRROR));
            assertFalse(ids.contains(TOWER_PRINCESS));
        }
    }

    @Test
    void 日付はUTCで区切り日本時間9時に替わる() {
        DailyQuiz before = service(Instant.parse("2026-10-27T23:59:59Z")).today();
        DailyQuiz after = service(Instant.parse("2026-10-28T00:00:00Z")).today();

        assertEquals(LocalDate.of(2026, 10, 27), before.date());
        assertEquals(LocalDate.of(2026, 10, 28), after.date());
        assertEquals(1, before.number());
        assertEquals(2, after.number());
    }

    @Test
    void 時計のタイムゾーンに関係なくUTCの日付を使う() {
        // 日本時間 2026-10-28 08:59 はUTCではまだ27日。
        Clock tokyo = Clock.fixed(Instant.parse("2026-10-27T23:59:00Z"), ZoneId.of("Asia/Tokyo"));

        assertEquals(LocalDate.of(2026, 10, 27), new QuizService(apiClient, cardStatsService, tokyo).today().date());
    }

    private QuizService service(Instant now) {
        return new QuizService(apiClient, cardStatsService, Clock.fixed(now, ZoneId.of("UTC")));
    }

    private static CardsResponse.Card card(int id, Integer elixirCost) {
        return new CardsResponse.Card(id, "Card" + id, 16, null, elixirCost, "common", null);
    }
}
