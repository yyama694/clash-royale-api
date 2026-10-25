package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardStatsTest {

    private static final List<String> LINES = List.of(
            "# コメント",
            "@asof\t2026-10-25",
            "Dart Goblin\tnormal\tstat.TID_SPELL_ATTRIBUTE_HEALTH\t417\t261\t",
            "Dart Goblin\tnormal\tstat.TID_SPELL_ATTRIBUTE_HIT_SPEED\t{s:0.8}\t{s:0.8}\t",
            "Dart Goblin\tevolution\tstat.TID_POISON_DPS\t102-490\t64-307\t",
            "Dart Goblin\tnormal\t=気絶効果時間\t0.5秒\t0.5秒\tja",
            "Inferno Tower\tnormal\tstat.TID_SPELL_ATTRIBUTE_DAMAGE_PER_SECOND\t172-3382\t\t",
            "");

    @Test
    void カードごとに画面の順のまま読みレベル11が空ならnullにする() {
        CardStats stats = CardStats.parse(LINES);

        assertEquals(LocalDate.of(2026, 10, 25), stats.asOf());
        List<CardStats.Row> dart = stats.of("Dart Goblin");
        assertEquals(4, dart.size());
        assertEquals(new CardStats.Row(CardStats.Form.NORMAL, "stat.TID_SPELL_ATTRIBUTE_HEALTH", "417", "261", null),
                dart.get(0));
        assertEquals(CardStats.Form.EVOLUTION, dart.get(2).form());
        assertNull(stats.of("Inferno Tower").get(0).level11());
        assertTrue(dart.get(3).shownIn("ja"));
        assertFalse(dart.get(3).shownIn("en"));
        assertTrue(dart.get(0).shownIn("en"));
    }

    @Test
    void 載っていないカードは空() {
        assertTrue(CardStats.parse(LINES).of("King Tower").isEmpty());
    }

    @Test
    void 読み取った日が無いファイルは読まない() {
        assertThrows(IllegalArgumentException.class,
                () -> CardStats.parse(List.of("Knight\tnormal\tstat.TID_SPELL_ATTRIBUTE_HEALTH\t2822\t1766\t")));
    }
}
