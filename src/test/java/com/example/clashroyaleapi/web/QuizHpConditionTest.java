package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.config.IcuMessageSource;
import com.example.clashroyaleapi.domain.CardStats;
import com.example.clashroyaleapi.service.CardStatsService;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * クイズのヒットポイントの並べ替えで、別のユニットや姿が出るカードに「どの姿の値か」の注釈を書き忘れないためのテスト。
 * 本物のステータス(cards/stats.tsv)と文言(messages_ja)で確かめる。読み取り直して新しいカードが入ったときに落ちる。
 */
class QuizHpConditionTest {

    /**
     * 別のユニットや姿が出ることを示すステータスの行。サブユニットのレベル(ステルスゴブリンレベルなど)・死亡時出現と、
     * 公式の訳が無く日本語の表記のまま書いた「〇〇レベル」「〇〇の数」(モンスターレベル・ライダーの数など)。
     */
    private static boolean showsOtherUnit(CardStats.Row row) {
        String key = row.labelKey();
        return key.matches("stat\\.TID_CHARACTER_.*\\.level") || key.equals("stat.TID_SPELL_ATTRIBUTE_DEATH_SPAWN")
                || key.matches("=.*(レベル|の数)");
    }

    @Test
    void 別のユニットや姿が出るカードにはどの姿のヒットポイントかの注釈がある() {
        CardStats stats = new CardStatsService().stats();
        LabelResolver labels = new LabelResolver(IcuMessageSource.forBasename("messages"));

        List<String> missing = stats.byCard().keySet().stream()
                .filter(card -> !card.endsWith(" (Ground)"))
                .filter(card -> stats.health(card).isPresent())
                .filter(card -> stats.of(card).stream()
                        .anyMatch(row -> row.form() == CardStats.Form.NORMAL && showsOtherUnit(row)))
                .filter(card -> labels.optionalMessage("quiz.hp.condition." + card, Locale.JAPANESE) == null)
                .toList();

        assertEquals(List.of(), missing);
    }
}
