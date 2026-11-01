package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.domain.CardStats;
import com.example.clashroyaleapi.domain.DailyQuiz;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * クイズの「呪文一撃で倒せるのはどれ?」を、本物のステータス(cards/stats.tsv)で確かめるテスト。
 * 問題文は「同じレベルのとき」なので、倒せるかどうかがレベル11とレベル16で変わらないことを確かめる
 * (読み取り直して値が変わったときに落ちる)。ユニット(cards/units.tsv)はレベル11の値が無いので対象外。
 */
class QuizSpellDataTest {

    private static final Pattern WHOLE_NUMBER = Pattern.compile("(\\d+)(?: x(\\d+))?");

    private final CardStats stats = new CardStatsService().stats();

    @Test
    void 出題する呪文はすべてステータスからダメージが出せる() {
        List<String> missing = Stream.concat(QuizService.HIT_SPELLS.stream(), QuizService.OVER_TIME_SPELLS.stream())
                .filter(name -> spell(name).isEmpty())
                .toList();

        assertEquals(List.of(), missing);
    }

    @Test
    void 倒せるかどうかはレベル11とレベル16で変わらない() {
        List<String> flips = new ArrayList<>();
        for (String name : Stream.concat(QuizService.HIT_SPELLS.stream(), QuizService.OVER_TIME_SPELLS.stream()).toList()) {
            DailyQuiz.Spell spell16 = spell(name).orElseThrow();
            int damage11 = level11Damage(name, false);
            for (String card : stats.byCard().keySet()) {
                if (card.endsWith(" (Ground)") || stats.health(card).isEmpty()) {
                    continue;
                }
                boolean building = stats.has(card, CardStats.LIFETIME);
                if (building && name.equals("Earthquake")) {
                    // アースクエイクの建物への毎秒ダメージは、レベル11の値がステータスに無い(倍率で求められない)
                    continue;
                }
                int health16 = stats.health(card).getAsInt();
                int health11 = health11(card);
                int damage16 = building ? spell16.buildingDamage() : spell16.damage();
                if ((health16 <= damage16) != (health11 <= damage11)) {
                    flips.add(name + " / " + card);
                }
            }
        }

        assertEquals(List.of(), flips);
    }

    private Optional<DailyQuiz.Spell> spell(String name) {
        return QuizService.spell(stats, new CardsResponse.Card(1, name, 16, null, 3, "rare", null));
    }

    private int health11(String card) {
        return stats.of(card).stream()
                .filter(row -> row.form() == CardStats.Form.NORMAL && row.labelKey().equals(CardStats.HEALTH))
                .map(row -> number(row.level11()))
                .findFirst()
                .orElseThrow();
    }

    /** レベル11の呪文のダメージ。QuizService と同じ出し方(時間で削る呪文は毎秒ダメージ×効果時間)。 */
    private int level11Damage(String name, boolean building) {
        List<CardStats.Row> rows = stats.of(name).stream().filter(row -> row.form() == CardStats.Form.NORMAL).toList();
        if (QuizService.HIT_SPELLS.contains(name)) {
            return rows.stream()
                    .filter(row -> row.labelKey().equals("stat.TID_SPELL_ATTRIBUTE_AREA_DAMAGE")
                            || row.labelKey().equals("stat.TID_SPELL_ATTRIBUTE_DAMAGE"))
                    .map(row -> number(row.level11()))
                    .findFirst()
                    .orElseThrow();
        }
        int perSecond = rows.stream()
                .filter(row -> row.labelKey().equals(building ? "stat.TID_SPELL_ATTRIBUTE_BUILDING_DPS"
                        : "stat.TID_SPELL_ATTRIBUTE_DAMAGE_PER_SECOND"))
                .map(row -> number(row.level11()))
                .findFirst()
                .orElseThrow();
        double seconds = rows.stream()
                .filter(row -> row.labelKey().equals("stat.TID_SPELL_ATTRIBUTE_DURATION")
                        || row.labelKey().equals("stat.TID_SPELL_ATTRIBUTE_CURSE_DURATION"))
                .map(row -> Double.parseDouble(row.level16().replaceAll("[^\\d.]", "")))
                .findFirst()
                .orElseThrow();
        return (int) (perSecond * seconds);
    }

    private static int number(String value) {
        Matcher m = WHOLE_NUMBER.matcher(value);
        if (!m.matches()) {
            throw new IllegalArgumentException("not a number: " + value);
        }
        return Integer.parseInt(m.group(1)) * (m.group(2) == null ? 1 : Integer.parseInt(m.group(2)));
    }
}
