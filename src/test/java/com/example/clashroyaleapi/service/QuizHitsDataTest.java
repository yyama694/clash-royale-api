package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.domain.CardStats;
import com.example.clashroyaleapi.domain.DailyQuiz;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「何発で倒せる?」を、本物のステータス(cards/stats.tsv)で確かめるテスト。読み取り直して値が変わったときに、
 * 例に挙げた組の答えが変わっていないか・出せる組が減りすぎていないかで落ちる。
 */
class QuizHitsDataTest {

    private final CardStats stats = new CardStatsService().stats();

    private final List<CardsResponse.Card> cards = new ArrayList<>();

    private final Map<String, Integer> hits = new HashMap<>();

    QuizHitsDataTest() {
        int id = 0;
        for (String name : stats.byCard().keySet()) {
            if (!name.endsWith(" (Ground)")) {
                cards.add(new CardsResponse.Card(id++, name, 16, null, 3, "common", null));
            }
        }
        for (DailyQuiz.Matchup m : QuizService.matchups(stats, cards)) {
            hits.put(cards.get(m.attackerId()).name() + " > " + cards.get(m.defenderId()).name(), m.hits());
        }
    }

    @Test
    void 毎日出しても足りるだけの組がある() {
        // 2026-11-25に数えたときは約2,000組
        assertTrue(hits.size() > 1500, () -> "組の数: " + hits.size());
        assertTrue(hits.values().stream().allMatch(h -> h >= DailyQuiz.HITS_MIN && h <= DailyQuiz.HITS_MAX));
    }

    @Test
    void よく知られた組の答え() {
        assertEquals(3, hits.get("P.E.K.K.A > Knight"));
        assertEquals(3, hits.get("Mini P.E.K.K.A > Hog Rider"));
        assertEquals(3, hits.get("Hog Rider > Cannon"));
        assertEquals(2, hits.get("Knight > Archers"));
    }

    @Test
    void 出さない組() {
        // 1発(ミニP.E.K.K.A→マスケット銃士)と7発(バルキリー→ナイト)は範囲の外
        assertEquals(Optional.empty(), Optional.ofNullable(hits.get("Mini P.E.K.K.A > Musketeer")));
        assertEquals(Optional.empty(), Optional.ofNullable(hits.get("Valkyrie > Knight")));
        // 地上しか攻撃しないナイトはガーゴイルに当たらず、建物しか狙わないホグライダーはナイトを攻撃しない
        assertTrue(hits.keySet().stream().noneMatch(pair -> pair.equals("Knight > Minions")));
        assertTrue(hits.keySet().stream().noneMatch(pair -> pair.equals("Hog Rider > Knight")));
        // 突進・ジャンプ・連鎖・インフェルノ・自爆・1回で消えるカードは攻撃する側に、シールド・自己回復は倒される側に出さない
        for (String attacker : List.of("Prince", "Mega Knight", "Electro Dragon", "Inferno Tower", "Goblin Demolisher",
                "Fire Spirit", "Wall Breakers", "Three Musketeers")) {
            assertTrue(hits.keySet().stream().noneMatch(pair -> pair.startsWith(attacker + " > ")), attacker);
        }
        for (String defender : List.of("Guards", "Dark Prince", "Royal Recruits", "Battle Healer", "Cannon Cart")) {
            assertTrue(hits.keySet().stream().noneMatch(pair -> pair.endsWith(" > " + defender)), defender);
        }
    }
}
