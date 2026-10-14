package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalanceUpdateTest {

    @Test
    void 調整を新しい順に読み_数値と変化率の無い行も読める() {
        List<BalanceUpdate> updates = BalanceUpdate.parse(List.of(
                "# コメント",
                "update\t2026-09-01\thttps://example.com/sep",
                "change\tKnight\tnormal\tbuff\thitpoints\t100\t110\t10",
                "",
                "update\t2026-10-06\thttps://example.com/oct",
                "change\tSkeletons\tnormal\tnerf\ttighterSpawnSpread",
                "change\tGoblin Drill\tnormal\tbuff\tcrownTowerSpawnDamage\t0\t20"));

        assertEquals(List.of(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 9, 1)),
                updates.stream().map(BalanceUpdate::date).toList());
        BalanceUpdate.Change spread = updates.getFirst().changes().get(0);
        assertFalse(spread.hasValues());
        BalanceUpdate.Change drill = updates.getFirst().changes().get(1);
        assertEquals("20", drill.after());
        assertNull(drill.percent());
        assertEquals(10, updates.get(1).changes().getFirst().percent());
    }

    @Test
    void 書式の崩れた行があれば失敗する() {
        assertThrows(IllegalArgumentException.class, () -> BalanceUpdate.parse(List.of(
                "change\tKnight\tnormal\tbuff\thitpoints\t100\t110\t10")));
        assertThrows(IllegalArgumentException.class, () -> BalanceUpdate.parse(List.of(
                "update\t2026-10-06\thttps://example.com",
                "change\tKnight\tnormal\tbuff\thitpoints\t100")));
    }

    @Test
    void 種類の順にカードと形ごとにまとめる() {
        BalanceUpdate update = BalanceUpdate.parse(List.of(
                "update\t2026-10-06\thttps://example.com",
                "change\tLava Hound\tnormal\tbuff\tdamage\t53\t71\t33",
                "change\tIce Wizard\thero\tnerf\tfreezeDamage\t89\t46\t-49",
                "change\tIce Wizard\thero\tnerf\tfreezeIgnoresSkeletonsBats",
                "change\tLava Hound\tnormal\tbuff\thitSpeed\t1.3s\t1.5s\t15",
                "change\tRoyal Ghost\tnormal\tnerf\thitpoints\t1210\t1152\t-5",
                "change\tRoyal Ghost\tnormal\tfix\tdamage\t261\t263\t1")).getFirst();

        List<BalanceUpdate.CardChanges> byCard = update.byCard();

        assertEquals(List.of("Ice Wizard", "Royal Ghost", "Lava Hound", "Royal Ghost"),
                byCard.stream().map(BalanceUpdate.CardChanges::card).toList());
        assertEquals(CardForm.HERO, byCard.get(0).form());
        assertEquals(2, byCard.get(0).changes().size());
        assertEquals(2, byCard.get(2).changes().size());
        assertEquals(BalanceUpdate.Kind.FIX, byCard.get(3).kind());
        assertTrue(update.touches("Lava Hound"));
        assertFalse(update.touches("Knight"));
    }

    @Test
    void 公開しているファイルが読める() throws IOException {
        for (String resource : List.of("balance/changes.tsv", "balance/usage-before.tsv")) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
                List<String> lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
                if (resource.endsWith("changes.tsv")) {
                    assertFalse(BalanceUpdate.parse(lines).isEmpty());
                } else {
                    assertFalse(BalanceBaseline.parse(lines).isEmpty());
                }
            }
        }
    }
}
