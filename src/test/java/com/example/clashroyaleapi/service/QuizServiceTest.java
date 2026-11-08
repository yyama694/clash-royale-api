package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.domain.CardAbility;
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
        DailyQuiz before = service(Instant.parse("2026-11-03T23:59:59Z")).today();
        DailyQuiz after = service(Instant.parse("2026-11-04T00:00:00Z")).today();

        assertEquals(LocalDate.of(2026, 11, 3), before.date());
        assertEquals(LocalDate.of(2026, 11, 4), after.date());
        assertEquals(1, before.number());
        assertEquals(2, after.number());
    }

    @Test
    void 今日の回は問題を作るときと同じ() {
        QuizService service = service(Instant.parse("2026-11-05T12:00:00Z"));

        DailyQuiz.Round round = service.todayRound();

        assertEquals(new DailyQuiz.Round(LocalDate.of(2026, 11, 5), 3), round);
        assertEquals(service.today().number(), round.number());
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
                    case QuizQuestion.SpellKill spellKill -> spellKill.cards().stream().map(QuizQuestion.HpCard::id);
                    case QuizQuestion.AbilityCost ability -> Stream.of(ability.cardId());
                })
                .toList();
    }

    @Test
    void 呪文のダメージはステータスから出し時間で削る呪文は効果時間いっぱいの合計にする() {
        CardStats stats = new CardStats(LocalDate.of(2026, 10, 25), Map.of(
                "Fireball", List.of(row("stat.TID_SPELL_ATTRIBUTE_AREA_DAMAGE", "1100")),
                "Arrows", List.of(row("stat.TID_SPELL_ATTRIBUTE_AREA_DAMAGE", "196 x3")),
                "Lightning", List.of(row("stat.TID_SPELL_ATTRIBUTE_DAMAGE", "1689")),
                "The Log", List.of(row("stat.TID_SPELL_ATTRIBUTE_AREA_DAMAGE", "429")),
                "Poison", List.of(row("stat.TID_SPELL_ATTRIBUTE_DAMAGE_PER_SECOND", "147"),
                        row("stat.TID_SPELL_ATTRIBUTE_DURATION", "{s:8}")),
                "Earthquake", List.of(row("stat.TID_SPELL_ATTRIBUTE_DAMAGE_PER_SECOND", "130"),
                        row("stat.TID_SPELL_ATTRIBUTE_BUILDING_DPS", "455"), row("stat.TID_SPELL_ATTRIBUTE_DURATION", "{s:3}")),
                "Goblin Curse", List.of(row("stat.TID_SPELL_ATTRIBUTE_DAMAGE_PER_SECOND", "57"),
                        row("stat.TID_SPELL_ATTRIBUTE_CURSE_DURATION", "{s:6}")),
                "Void", List.of(row("stat.TID_DAMAGE_WITH_SINGLE_TARGET", "1112"))));

        assertEquals(new DailyQuiz.Spell(1, 1100, 1100, false, false), spell(stats, 1, "Fireball"));
        // 矢の雨は3回とも当たったとき
        assertEquals(new DailyQuiz.Spell(2, 588, 588, false, false), spell(stats, 2, "Arrows"));
        assertEquals(new DailyQuiz.Spell(3, 1689, 1689, false, false), spell(stats, 3, "Lightning"));
        assertEquals(new DailyQuiz.Spell(4, 429, 429, true, false), spell(stats, 4, "The Log"));
        assertEquals(new DailyQuiz.Spell(5, 1176, 1176, false, true), spell(stats, 5, "Poison"));
        // アースクエイクは地上だけで、建物へのダメージが違う
        assertEquals(new DailyQuiz.Spell(6, 390, 1365, true, true), spell(stats, 6, "Earthquake"));
        assertEquals(new DailyQuiz.Spell(7, 342, 342, false, true), spell(stats, 7, "Goblin Curse"));
        // ボイドは当たった数でダメージが変わるので出さない。ステータスの無い呪文も出さない
        assertEquals(null, spell(stats, 8, "Void"));
        assertEquals(null, spell(stats, 9, "Zap"));
    }

    @Test
    void 地上だけの呪文の問題には空を飛ぶカードを出さず建物にはアースクエイクの建物へのダメージで考える() {
        // 空を飛ぶカード(ガーゴイル)と建物(稼働時間のあるカード)を足し、アースクエイクだけを出題させる
        List<CardsResponse.Card> items = new ArrayList<>(apiClient.getCards().items());
        items.add(new CardsResponse.Card(200, "Minions", 16, null, 3, "common", null));
        items.add(new CardsResponse.Card(201, "Earthquake", 16, null, 3, "rare", null));
        when(apiClient.getCards()).thenReturn(new CardsResponse(items, List.of()));
        Map<String, List<CardStats.Row>> rows = new HashMap<>(cardStatsService.stats().byCard());
        rows.put("Minions", List.of(row(CardStats.HEALTH, "368")));
        rows.put("Card8", List.of(row(CardStats.HEALTH, "1200"), row(CardStats.LIFETIME, "{s:30}")));
        rows.put("Earthquake", List.of(row("stat.TID_SPELL_ATTRIBUTE_DAMAGE_PER_SECOND", "130"),
                row("stat.TID_SPELL_ATTRIBUTE_BUILDING_DPS", "455"), row("stat.TID_SPELL_ATTRIBUTE_DURATION", "{s:3}")));
        CardStatsService withSpell = new CardStatsService(new CardStats(LocalDate.of(2026, 10, 25), rows));

        Set<Integer> candidates = new HashSet<>();
        for (int day = 0; day < 60; day++) {
            Clock clock = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z").plusSeconds(day * 86_400L), ZoneId.of("UTC"));
            for (QuizQuestion question : new QuizService(apiClient, withSpell, clock).today().questions()) {
                if (question instanceof QuizQuestion.SpellKill spellKill) {
                    assertEquals(201, spellKill.spellId());
                    for (int i = 0; i < spellKill.cards().size(); i++) {
                        int id = spellKill.cards().get(i).id();
                        candidates.add(id);
                        assertEquals(id == 8 ? 1365 : 390, spellKill.damages().get(i));
                    }
                }
            }
        }
        assertFalse(candidates.contains(200));
        assertTrue(candidates.contains(8));
    }

    @Test
    void スキルのコストの問題は公式APIのカードのIDで出し公式APIに無いカードのスキルは出さない() {
        CardStatsService withAbilities = new CardStatsService(cardStatsService.stats(), List.of(), List.of(
                new CardAbility("Card3", false, 2), new CardAbility("Card12", true, 1),
                new CardAbility("Removed Card", false, 5)));
        Map<Integer, Set<String>> seen = new HashMap<>();
        for (int day = 0; day < 30; day++) {
            Clock clock = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z").plusSeconds(day * 86_400L), ZoneId.of("UTC"));
            for (QuizQuestion question : new QuizService(apiClient, withAbilities, clock).today().questions()) {
                if (question instanceof QuizQuestion.AbilityCost ability) {
                    seen.computeIfAbsent(ability.cardId(), id -> new HashSet<>())
                            .add(ability.champion() + ":" + ability.correctChoice());
                }
            }
        }
        assertEquals(Map.of(3, Set.of("false:2"), 12, Set.of("true:1")), seen);
    }

    private static DailyQuiz.Spell spell(CardStats stats, int id, String name) {
        return QuizService.spell(stats, new CardsResponse.Card(id, name, 16, null, 3, "rare", null)).orElse(null);
    }

    private static CardStats.Row row(String labelKey, String level16) {
        return new CardStats.Row(CardStats.Form.NORMAL, labelKey, level16, level16, null);
    }

    private static CardsResponse.Card card(int id, Integer elixirCost) {
        return new CardsResponse.Card(id, "Card" + id, 16, null, elixirCost, "common", null);
    }
}
