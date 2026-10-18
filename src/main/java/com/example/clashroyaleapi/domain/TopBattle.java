package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.ApiTimestamp;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 世界上位のプレイヤーどうしのランク戦1戦(デッキ集計 TopDeckCollector が取った対戦履歴から)。
 * 同じ対戦は両者の対戦履歴に1回ずつ現れる。公式APIの対戦には一意のIDが無いので、2人をタグの順に並べ、
 * 時刻と2人のタグ({@link #key()})で同じ対戦とみなす。
 */
public record TopBattle(Instant time, Side first, Side second) {

    private static final String RANKED_BATTLE_TYPE = "pathOfLegend";
    private static final int DECK_SIZE = 8;

    /** elixirLeaked は消費漏れ({@link ElixirLeak})。 */
    public record Side(String tag, List<Integer> cardIds, int crowns, double elixirLeaked) {

        public boolean contains(int cardId) {
            return cardIds.contains(cardId);
        }
    }

    /** 1対1のランク戦で、両者のデッキが8枚そろい消費漏れも入っているものだけ。 */
    public static Optional<TopBattle> of(BattleLogEntry battle) {
        if (!RANKED_BATTLE_TYPE.equals(battle.type()) || battle.team() == null || battle.team().size() != 1
                || battle.opponent() == null || battle.opponent().size() != 1) {
            return Optional.empty();
        }
        Optional<Side> me = side(battle.team().get(0));
        Optional<Side> opponent = side(battle.opponent().get(0));
        Optional<Instant> time = ApiTimestamp.parse(battle.battleTime());
        if (me.isEmpty() || opponent.isEmpty() || time.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ordered(time.get(), me.get(), opponent.get()));
    }

    private static Optional<Side> side(BattleLogEntry.Participant participant) {
        if (participant.tag() == null || participant.elixirLeaked() == null || participant.cards() == null
                || participant.cards().size() != DECK_SIZE) {
            return Optional.empty();
        }
        return Optional.of(new Side(participant.tag(),
                participant.cards().stream().map(BattleLogEntry.Card::id).toList(),
                participant.crowns(), participant.elixirLeaked()));
    }

    static TopBattle ordered(Instant time, Side a, Side b) {
        return a.tag().compareTo(b.tag()) <= 0 ? new TopBattle(time, a, b) : new TopBattle(time, b, a);
    }

    public String key() {
        return time + " " + first.tag() + " " + second.tag();
    }

    /** 引き分けなら空。 */
    public Optional<Side> winner() {
        return first.crowns() == second.crowns() ? Optional.empty()
                : Optional.of(first.crowns() > second.crowns() ? first : second);
    }

    /** 引き分けなら空。 */
    public Optional<Side> loser() {
        return winner().map(winner -> winner == first ? second : first);
    }
}
