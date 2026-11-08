package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.config.IcuMessageSource;
import com.example.clashroyaleapi.domain.CardAbility;
import com.example.clashroyaleapi.domain.CardStats;
import com.example.clashroyaleapi.service.CardStatsService;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ヒーローとチャンピオンのスキルのコスト(cards/abilities.tsv)と、スキル名の文言(ability.*)の抜けを見つけるテスト。
 * 本物のデータで確かめるので、ステータスを読み取り直して新しいヒーロー・チャンピオンが入ったときに落ちる。
 */
class QuizAbilityDataTest {

    private final CardStatsService cardStatsService = new CardStatsService();
    private final LabelResolver labels = new LabelResolver(IcuMessageSource.forBasename("messages"));

    @Test
    void ステータスにヒーローかチャンピオンのスキルがあるカードとスキルのコストのカードが一致する() {
        CardStats stats = cardStatsService.stats();
        Map<String, Boolean> fromStats = new TreeMap<>();
        stats.byCard().forEach((card, rows) -> rows.stream()
                .filter(row -> row.form() == CardStats.Form.HERO || row.form() == CardStats.Form.CHAMPION)
                .findFirst()
                .ifPresent(row -> fromStats.put(card, row.form() == CardStats.Form.CHAMPION)));
        Map<String, Boolean> fromAbilities = cardStatsService.abilities().stream()
                .collect(Collectors.toMap(CardAbility::card, CardAbility::champion, (a, b) -> a, TreeMap::new));

        assertEquals(fromStats, fromAbilities);
    }

    @Test
    void スキルのコストは1から9で同じカードは1行だけ() {
        List<CardAbility> abilities = cardStatsService.abilities();

        assertEquals(abilities.size(), abilities.stream().map(CardAbility::card).distinct().count());
        abilities.forEach(ability -> assertTrue(ability.cost() >= 1 && ability.cost() <= 9, ability::toString));
    }

    @Test
    void すべてのスキルに10言語のスキル名がある() {
        for (CardAbility ability : cardStatsService.abilities()) {
            String english = labels.message("ability." + ability.card(), Locale.ENGLISH);
            for (Locale locale : SupportedLanguages.SUPPORTED) {
                String name = labels.message("ability." + ability.card(), locale);
                assertNotEquals("ability." + ability.card(), name, () -> ability.card() + " " + locale);
                if (!locale.getLanguage().equals("en")) {
                    // 英語のまま写し忘れていないか(英語と同じ名前の言語があれば、ここに例外を足す)
                    assertNotEquals(english, name, () -> ability.card() + " " + locale);
                }
            }
        }
    }
}
