package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.config.IcuMessageSource;
import com.example.clashroyaleapi.domain.CardStats;
import com.example.clashroyaleapi.domain.CardUnit;
import com.example.clashroyaleapi.service.CardStatsService;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * クイズのヒットポイントの並べ替えで、別のユニットや姿が出るカードに「どの姿の値か」の注釈を書き忘れないためのテスト。
 * 本物のステータス(cards/stats.tsv・cards/units.tsv)と文言(messages_ja)で確かめる。読み取り直して新しいカードが入ったときに落ちる。
 */
class QuizHpConditionTest {

    /**
     * ステータスにサブユニットの行はあるが、遊ぶ人から見ると最初から最後まで1つのキャラで、注釈の要らないカード。
     * ラムライダーの「ライダーの数」「ライダーレベル」は、ラムと一緒に動いて一緒に倒れる乗り手(2026-10-31にユーザーの指摘)。
     */
    private static final Set<String> ONE_CHARACTER = Set.of("Ram Rider");

    private final CardStatsService cardStatsService = new CardStatsService();
    private final LabelResolver labels = new LabelResolver(IcuMessageSource.forBasename("messages"));

    /**
     * 別のユニットや姿が出ることを示すステータスの行。サブユニットのレベル(ステルスゴブリンレベルなど)・死亡時出現と、
     * 公式の訳が無く日本語の表記のまま書いた「〇〇レベル」「〇〇の数」(モンスターレベルなど)。
     */
    private static boolean showsOtherUnit(CardStats.Row row) {
        String key = row.labelKey();
        return key.matches("stat\\.TID_CHARACTER_.*\\.level") || key.equals("stat.TID_SPELL_ATTRIBUTE_DEATH_SPAWN")
                || key.matches("=.*(レベル|の数)");
    }

    @Test
    void 別のユニットや姿が出るカードにはどの姿のヒットポイントかの注釈がある() {
        CardStats stats = cardStatsService.stats();

        List<String> missing = stats.byCard().keySet().stream()
                .filter(card -> !card.endsWith(" (Ground)") && !ONE_CHARACTER.contains(card))
                .filter(card -> stats.health(card).isPresent())
                .filter(card -> stats.of(card).stream()
                        .anyMatch(row -> row.form() == CardStats.Form.NORMAL && showsOtherUnit(row)))
                .filter(card -> condition(card) == null)
                .toList();

        assertEquals(List.of(), missing);
    }

    @Test
    void 一つのキャラのカードには注釈を付けない() {
        ONE_CHARACTER.forEach(card -> assertEquals(null, condition(card), card));
    }

    @Test
    void カードとは別に出すユニットにはどのユニットかの注釈がありヒットポイントのある元のカードにも注釈がある() {
        List<String> missing = cardStatsService.units().stream()
                .flatMap(unit -> cardStatsService.stats().health(unit.card()).isPresent()
                        ? List.of(unit.card() + "." + unit.unit(), unit.card()).stream()
                        : List.of(unit.card() + "." + unit.unit()).stream())
                .distinct()
                .filter(key -> condition(key) == null)
                .toList();

        assertEquals(List.of(), missing);
    }

    @Test
    void リトルプリンスのガーディアンはステータスのスキルのヒットポイントと同じ() {
        int guardian = cardStatsService.units().stream()
                .filter(unit -> unit.card().equals("Little Prince") && unit.unit().equals("guardian"))
                .mapToInt(CardUnit::health)
                .findFirst()
                .orElseThrow();
        String skill = cardStatsService.stats().of("Little Prince").stream()
                .filter(row -> row.form() == CardStats.Form.CHAMPION && row.labelKey().equals(CardStats.HEALTH))
                .map(CardStats.Row::level16)
                .findFirst()
                .orElseThrow();

        assertEquals(skill, String.valueOf(guardian));
    }

    private String condition(String key) {
        return labels.optionalMessage("quiz.hp.condition." + key, Locale.JAPANESE);
    }
}
