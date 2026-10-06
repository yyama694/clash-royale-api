package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RankingMovementsTest {

    private static RankingSnapshot.Entry entry(int rank, String name, int rating) {
        return new RankingSnapshot.Entry(rank, "#" + name, name, rating);
    }

    private static RankingSnapshot snapshot(String at, RankingSnapshot.Entry... entries) {
        return new RankingSnapshot(Instant.parse(at), "2026-09", List.of(entries));
    }

    private static final RankingSnapshot BEFORE = snapshot("2026-10-05T12:00:00Z",
            entry(1, "A", 3000), entry(2, "B", 2990), entry(3, "C", 2980), entry(4, "D", 2970), entry(5, "E", 2960));

    private static final RankingSnapshot AFTER = snapshot("2026-10-06T12:00:00Z",
            entry(1, "B", 3010), entry(2, "A", 3000), entry(3, "F", 2995), entry(4, "E", 2990), entry(5, "D", 2950));

    @Test
    void 上位の前の順位とレーティングの増減を出し前に載っていない人は前の順位を空にする() {
        RankingMovements movements = RankingMovements.between(BEFORE, AFTER, 3, 10);

        assertEquals(List.of(
                new RankingMovements.TopEntry(entry(1, "B", 3010), 2, 20),
                new RankingMovements.TopEntry(entry(2, "A", 3000), 1, 0),
                new RankingMovements.TopEntry(entry(3, "F", 2995), null, null)), movements.top());
    }

    @Test
    void レーティングを上げた幅の大きい順に並べ前に載っていない人と下げた人は入れない() {
        RankingMovements movements = RankingMovements.between(BEFORE, AFTER, 3, 10);

        assertEquals(List.of(
                new RankingMovements.Climber(entry(4, "E", 2990), 5, 30),
                new RankingMovements.Climber(entry(1, "B", 3010), 2, 20)), movements.climbers());
    }

    @Test
    void レーティングを上げた人は上限の人数までにする() {
        assertEquals(1, RankingMovements.between(BEFORE, AFTER, 3, 1).climbers().size());
    }

    @Test
    void 上位から外れた人を今の順位付きで前の順位の順に出しランキングから消えた人は今の順位を空にする() {
        RankingMovements movements = RankingMovements.between(BEFORE, AFTER, 3, 10);

        assertEquals(List.of(new RankingMovements.Dropout(entry(3, "C", 2980), null)), movements.dropouts());
        assertEquals(List.of(new RankingMovements.Dropout(entry(3, "C", 2980), null),
                        new RankingMovements.Dropout(entry(4, "D", 2970), entry(5, "D", 2950))),
                RankingMovements.between(BEFORE, AFTER, 4, 10).dropouts());
    }

    @Test
    void 比べる記録が無ければ今の上位だけを出す() {
        RankingMovements movements = RankingMovements.between(null, AFTER, 2, 10);

        assertNull(movements.before());
        assertEquals(List.of(new RankingMovements.TopEntry(entry(1, "B", 3010), null, null),
                new RankingMovements.TopEntry(entry(2, "A", 3000), null, null)), movements.top());
        assertEquals(List.of(), movements.climbers());
        assertEquals(List.of(), movements.dropouts());
    }
}
