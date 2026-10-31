package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.domain.CardStats;
import com.example.clashroyaleapi.domain.CardUnit;
import com.example.clashroyaleapi.domain.DailyQuiz;
import com.example.clashroyaleapi.domain.QuizQuestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuizServiceTest {

    private static final int MIRROR = 99;
    private static final int TOWER_PRINCESS = 100;

    // 地上に出すと3エリクサーになるカード(スピリットエンプレスの役)。公式APIのコストは6
    private static final int EMPRESS = 5;

    // 何体も出るカード(バーバリアンの役)と、シールドのあるカード(ダークプリンスの役)。どちらもヒットポイントはある
    private static final int BARBARIANS = 6;
    private static final int DARK_PRINCE = 7;

    // ヒットポイントのあるカード(0〜29)と、呪文(30〜39。ヒットポイントが無い)
    private static final int WITH_HEALTH = 30;

    private ClashRoyaleApiClient apiClient;
    private CardStatsService cardStatsService;

    @BeforeEach
    void setUp() {
        apiClient = mock(ClashRoyaleApiClient.class);
        List<CardsResponse.Card> items = new ArrayList<>();
        IntStream.range(0, 40).forEach(i -> items.add(card(i, 1 + i % 9)));
        items.add(card(MIRROR, null));
        when(apiClient.getCards()).thenReturn(new CardsResponse(items, List.of(card(TOWER_PRINCESS, null))));
        Map<String, List<CardStats.Row>> rows = new HashMap<>();
        IntStream.range(0, WITH_HEALTH).forEach(i -> rows.put("Card" + i, new ArrayList<>(List.of(
                row(CardStats.HEALTH, String.valueOf(100 + i * 150))))));
        rows.get("Card" + EMPRESS).add(row(CardStats.GROUND_ELIXIR, "3"));
        rows.get("Card" + BARBARIANS).add(row(CardStats.COUNT, "5"));
        rows.get("Card" + DARK_PRINCE).add(row(CardStats.SHIELD_HEALTH, "384"));
        cardStatsService = new CardStatsService(new CardStats(LocalDate.of(2026, 10, 25), rows));
    }

    @Test
    void カードとは別のユニットは元のカードのIDで並べ替えに出し公式APIに無いカードのユニットは出さない() {
        // Card35 はヒットポイントの無いカード(アウトローの役)。ユニットだけが並べ替えに出る
        CardStatsService withUnits = new CardStatsService(cardStatsService.stats(), List.of(
                new CardUnit("Card35", "boy", 7000), new CardUnit("Removed Card", "x", 9000)));
        Set<QuizQuestion.HpCard> units = new HashSet<>();
        for (int day = 0; day < 60; day++) {
            Clock clock = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z").plusSeconds(day * 86_400L), ZoneId.of("UTC"));
            new QuizService(apiClient, withUnits, clock).today().questions().stream()
                    .filter(QuizQuestion.HpOrder.class::isInstance)
                    .flatMap(question -> ((QuizQuestion.HpOrder) question).cards().stream())
                    .filter(card -> card.unit() != null)
                    .forEach(units::add);
        }
        assertEquals(Set.of(new QuizQuestion.HpCard(35, "boy", 7000)), units);
    }

    @Test
    void 地上に出すと別のコストになるカードは地上の形も別の問題として出す() {
        Set<Boolean> forms = new HashSet<>();
        for (int day = 0; day < 60; day++) {
            DailyQuiz quiz = service(Instant.parse("2026-10-26T12:00:00Z").plusSeconds(day * 86_400L)).today();

            for (QuizQuestion.Elixir question : elixirs(quiz)) {
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

            List<Integer> ids = cardIds(quiz);
            assertFalse(ids.contains(MIRROR));
            assertFalse(ids.contains(TOWER_PRINCESS));
        }
    }

    @Test
    void 並べ替えにはステータスのヒットポイントを使い何体も出るカードとシールドのあるカードも出し呪文は出さない() {
        Set<Integer> seen = new HashSet<>();
        for (int day = 0; day < 60; day++) {
            DailyQuiz quiz = service(Instant.parse("2026-10-26T12:00:00Z").plusSeconds(day * 86_400L)).today();

            for (QuizQuestion question : quiz.questions()) {
                if (question instanceof QuizQuestion.HpOrder hpOrder) {
                    for (QuizQuestion.HpCard card : hpOrder.cards()) {
                        assertEquals(100 + card.id() * 150, card.health());
                        seen.add(card.id());
                    }
                }
            }
        }
        // 1体あたり・シールドを除く値であることは、画面で注釈を添える(ViewMapper)
        assertTrue(seen.contains(BARBARIANS));
        assertTrue(seen.contains(DARK_PRINCE));
        assertTrue(seen.stream().allMatch(id -> id < WITH_HEALTH));
        // 地上に出せるカードも、霊龍の形のヒットポイントで出る(地上の形は同じ値なので出さない)
        assertTrue(seen.contains(EMPRESS));
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

    private static List<QuizQuestion.Elixir> elixirs(DailyQuiz quiz) {
        return quiz.questions().stream()
                .filter(QuizQuestion.Elixir.class::isInstance)
                .map(QuizQuestion.Elixir.class::cast)
                .toList();
    }

    private static List<Integer> cardIds(DailyQuiz quiz) {
        return quiz.questions().stream()
                .flatMap(question -> switch (question) {
                    case QuizQuestion.Elixir elixir -> Stream.of(elixir.cardId());
                    case QuizQuestion.HpOrder hpOrder -> hpOrder.cards().stream().map(QuizQuestion.HpCard::id);
                })
                .toList();
    }

    private static CardStats.Row row(String labelKey, String level16) {
        return new CardStats.Row(CardStats.Form.NORMAL, labelKey, level16, level16, null);
    }

    private static CardsResponse.Card card(int id, Integer elixirCost) {
        return new CardsResponse.Card(id, "Card" + id, 16, null, elixirCost, "common", null);
    }
}
