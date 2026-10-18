package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.ApiTimestamp;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 世界上位のプレイヤーの対戦履歴を1人分ずつ受け取り、デッキごとの勝率({@link DeckWinRates})を数える。
 *
 * 上位の人は勝ってランキングに入った人なので、上位の人の側だけを数えると勝率が高く出る
 * (2026-10-18の上位1000人では、本人の側の勝率が60.5%)。そこで両者とも上位の人の対戦だけを、両者の側から数える。
 * こうすると1戦ごとに勝ちと負けが1つずつ入り、全体の勝率はちょうど50%になる。上位どうしの対戦は全体の約6割だった。
 * 同じ対戦は両者の対戦履歴に出るので、日時と2人のタグで1回にまとめる。同じデッキどうしの対戦は勝率に効かないので数えない。
 * 対戦履歴はしばらく遊んでいない人だと前のシーズンまで遡るので、集めたランキングのシーズンの対戦だけを数える。
 */
public final class DeckWinRateTally {

    private static final String RANKED_BATTLE_TYPE = "pathOfLegend";

    private final Map<String, DeckWinRates.TopPlayer> topPlayers;
    private final Instant from;
    private final Instant until;
    private final Map<String, Battle> battles = new LinkedHashMap<>();

    /**
     * @param from  数える対戦の始まり(含む)
     * @param until 数える対戦の終わり(含まない)。今シーズンなら null
     */
    public DeckWinRateTally(List<DeckWinRates.TopPlayer> topPlayers, Instant from, Instant until) {
        this.topPlayers = topPlayers.stream()
                .collect(Collectors.toMap(DeckWinRates.TopPlayer::tag, player -> player, (a, b) -> a));
        this.from = from;
        this.until = until;
    }

    /** 1人分の対戦履歴。対戦の中身は、数え終わるまで持つのに要る分だけ残す。 */
    public void add(List<BattleLogEntry> log) {
        for (BattleLogEntry battle : log) {
            if (!counted(battle)) {
                continue;
            }
            Side team = Side.of(battle.team().get(0));
            Side opponent = Side.of(battle.opponent().get(0));
            String key = battle.battleTime() + Set.of(team.tag(), opponent.tag()).stream().sorted().toList();
            battles.putIfAbsent(key, new Battle(battle.battleTime(), team, opponent));
        }
    }

    private boolean counted(BattleLogEntry battle) {
        if (!RANKED_BATTLE_TYPE.equals(battle.type())
                || battle.team() == null || battle.team().size() != 1
                || battle.opponent() == null || battle.opponent().size() != 1) {
            return false;
        }
        BattleLogEntry.Participant team = battle.team().get(0);
        BattleLogEntry.Participant opponent = battle.opponent().get(0);
        if (!fullDeck(team) || !fullDeck(opponent)
                || !topPlayers.containsKey(team.tag()) || !topPlayers.containsKey(opponent.tag())
                || team.tag().equals(opponent.tag())) {
            return false;
        }
        return ApiTimestamp.parse(battle.battleTime())
                .filter(time -> !time.isBefore(from) && (until == null || time.isBefore(until)))
                .isPresent();
    }

    private static boolean fullDeck(BattleLogEntry.Participant participant) {
        return participant.tag() != null && participant.cards() != null && participant.cards().size() == Deck.SIZE;
    }

    public DeckWinRates result(Instant collectedAt, YearMonth finishedSeason) {
        Map<Set<Integer>, DeckCount> counts = new HashMap<>();
        int counted = 0;
        for (Battle battle : battles.values()) {
            if (battle.team().deck().equals(battle.opponent().deck())) {
                continue;
            }
            counted++;
            count(counts, battle, battle.team(), battle.opponent());
            count(counts, battle, battle.opponent(), battle.team());
        }
        List<DeckWinRates.DeckRecord> decks = counts.values().stream()
                .map(DeckCount::toRecord)
                .sorted(Comparator.comparingInt(DeckWinRates.DeckRecord::battles).reversed())
                .toList();
        return new DeckWinRates(collectedAt, finishedSeason, counted, decks);
    }

    private void count(Map<Set<Integer>, DeckCount> counts, Battle battle, Side side, Side other) {
        counts.computeIfAbsent(side.deck(), deck -> new DeckCount())
                .add(battle.time(), side, topPlayers.get(side.tag()), side.crowns() > other.crowns());
    }

    private record Battle(String time, Side team, Side opponent) {
    }

    private record Side(String tag, int crowns, List<Integer> cardIds, List<CardForm> forms, Integer towerTroopId,
                        Set<Integer> deck) {

        static Side of(BattleLogEntry.Participant participant) {
            List<Integer> cardIds = participant.cards().stream().map(BattleLogEntry.Card::id).toList();
            List<CardForm> forms = participant.cards().stream()
                    .map(card -> CardForm.ofBattle(card.evolutionLevel()))
                    .toList();
            Integer tower = participant.supportCards() == null || participant.supportCards().isEmpty()
                    ? null : participant.supportCards().get(0).id();
            return new Side(participant.tag(), participant.crowns(), cardIds, forms, tower, Set.copyOf(cardIds));
        }
    }

    private static final class DeckCount {

        private final Map<String, Integer> battlesByPlayer = new HashMap<>();
        private int battles;
        private int wins;
        private Side shown;
        private DeckWinRates.TopPlayer top;
        private String shownTime;

        void add(String time, Side side, DeckWinRates.TopPlayer player, boolean won) {
            battles++;
            if (won) {
                wins++;
            }
            battlesByPlayer.merge(side.tag(), 1, Integer::sum);
            // battleTime は同じ形式の文字列なので、文字列の順が時刻の順になる。
            if (top == null || player.rank() < top.rank()
                    || (player.rank() == top.rank() && time.compareTo(shownTime) > 0)) {
                top = player;
                shown = side;
                shownTime = time;
            }
        }

        DeckWinRates.DeckRecord toRecord() {
            int maxPlayerBattles = battlesByPlayer.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            return new DeckWinRates.DeckRecord(shown.cardIds(), shown.forms(), shown.towerTroopId(),
                    top, battles, wins, battlesByPlayer.size(), maxPlayerBattles);
        }
    }
}
