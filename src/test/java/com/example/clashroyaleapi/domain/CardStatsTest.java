package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;

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
    void タワーへのダメージは同じ欄のダメージから何パーセント減るかを求める() {
        CardStats.Row area = row(CardStats.Form.NORMAL, "stat.TID_SPELL_ATTRIBUTE_AREA_DAMAGE", "1100");
        CardStats.Row tower = row(CardStats.Form.NORMAL, "stat.TID_SPELL_ATTRIBUTE_TOWER_DAMAGE", "253");

        assertEquals(OptionalInt.of(77), CardStats.towerDamageReduction(tower, List.of(area, tower)));
        // ほかの項目には付けない
        assertEquals(OptionalInt.empty(), CardStats.towerDamageReduction(area, List.of(area, tower)));
    }

    @Test
    void 限界突破の欄に比べる相手が無ければ通常の欄のダメージと比べる() {
        // 巨大雪玉の限界突破は範囲ダメージが通常と同じなので、限界突破の欄にはタワーへのダメージだけが載る
        CardStats.Row area = row(CardStats.Form.NORMAL, "stat.TID_SPELL_ATTRIBUTE_AREA_DAMAGE", "286");
        CardStats.Row evoTower = row(CardStats.Form.EVOLUTION, "stat.TID_SPELL_ATTRIBUTE_TOWER_DAMAGE", "73");

        assertEquals(OptionalInt.of(74), CardStats.towerDamageReduction(evoTower, List.of(area, evoTower)));
    }

    @Test
    void 回数つきの値は回数がそろうときだけ比べ範囲の値には付けない() {
        CardStats.Row arrows = row(CardStats.Form.NORMAL, "stat.TID_SPELL_ATTRIBUTE_AREA_DAMAGE", "196 x3");
        CardStats.Row arrowsTower = row(CardStats.Form.NORMAL, "stat.TID_SPELL_ATTRIBUTE_TOWER_DAMAGE", "40 x3");
        assertEquals(OptionalInt.of(80), CardStats.towerDamageReduction(arrowsTower, List.of(arrows, arrowsTower)));

        CardStats.Row poison = row(CardStats.Form.EVOLUTION, "stat.TID_POISON_DPS", "102-490");
        CardStats.Row poisonTower = row(CardStats.Form.EVOLUTION, "stat.TID_SPELL_ATTRIBUTE_TOWER_DPS", "24-122");
        assertEquals(OptionalInt.empty(), CardStats.towerDamageReduction(poisonTower, List.of(poison, poisonTower)));
    }

    private static CardStats.Row row(CardStats.Form form, String key, String level16) {
        return new CardStats.Row(form, key, level16, null, null);
    }

    @Test
    void 載っていないカードは空() {
        assertTrue(CardStats.parse(LINES).of("King Tower").isEmpty());
    }

    @Test
    void 地上のエリクサーはゲームの画面の項目から読み地上の形は別のカードの名前で引く() {
        CardStats stats = CardStats.parse(List.of(
                "@asof\t2026-10-25",
                "Spirit Empress\tnormal\tstat.TID_SPELL_ATTRIBUTE_HEALTH\t1791\t1121\t",
                "Spirit Empress\tnormal\t" + CardStats.GROUND_ELIXIR + "\t3\t3\t",
                "Spirit Empress (Ground)\tnormal\tstat.TID_SPELL_ATTRIBUTE_HIT_SPEED\t{s:1.2}\t{s:1.2}\t"));

        assertEquals(OptionalInt.of(3), stats.groundElixir("Spirit Empress"));
        assertEquals(OptionalInt.empty(), stats.groundElixir("Knight"));
        assertEquals("Spirit Empress (Ground)", CardStats.groundCard("Spirit Empress"));
        assertEquals(1, stats.of(CardStats.groundCard("Spirit Empress")).size());
    }

    @Test
    void 読み取った日が無いファイルは読まない() {
        assertThrows(IllegalArgumentException.class,
                () -> CardStats.parse(List.of("Knight\tnormal\tstat.TID_SPELL_ATTRIBUTE_HEALTH\t2822\t1766\t")));
    }
}
