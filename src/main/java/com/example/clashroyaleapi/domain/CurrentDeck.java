package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.PlayerResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 使用中のデッキ(公式APIの currentDeck)。
 * currentDeck は条件によってチャンピオンを返さず7枚になる(ランク戦上位60人中19人。進捗ログ.mdのフェーズ1.80)。
 * そのときは、今のデッキのカードをすべて含む直近の1対1の対戦から、抜けているカードを補う。
 *
 * @param completedFromBattle 対戦履歴から補ったカードがあるか(画面で注記するため)
 */
public record CurrentDeck(List<BattleLogEntry.Card> cards, List<BattleLogEntry.Card> supportCards,
                          boolean completedFromBattle) {

    static final int DECK_SIZE = 8;

    /** @param battleLog 新しい順。取得できなかったときは空でよい(補わずにそのまま返す) */
    public static Optional<CurrentDeck> of(PlayerResponse player, List<BattleLogEntry> battleLog) {
        List<BattleLogEntry.Card> deck = player.currentDeck();
        if (deck == null || deck.isEmpty()) {
            return Optional.empty();
        }
        List<BattleLogEntry.Card> support = player.currentDeckSupportCards() == null
                ? List.of() : player.currentDeckSupportCards();
        if (deck.size() >= DECK_SIZE) {
            return Optional.of(new CurrentDeck(deck, support, false));
        }
        return Optional.of(battleLog.stream()
                .map(battle -> ownDeck(battle, player.tag()))
                .flatMap(Optional::stream)
                .filter(battleDeck -> containsAll(battleDeck, deck))
                .findFirst()
                .map(battleDeck -> new CurrentDeck(fill(deck, battleDeck), support, true))
                .orElseGet(() -> new CurrentDeck(deck, support, false)));
    }

    /** 1対1の対戦で、本人が8枚そろったデッキを使っていればそのデッキ。2v2は味方と区別がつきにくいので使わない。 */
    private static Optional<List<BattleLogEntry.Card>> ownDeck(BattleLogEntry battle, String playerTag) {
        if (battle.team() == null || battle.team().size() != 1) {
            return Optional.empty();
        }
        BattleLogEntry.Participant me = battle.team().get(0);
        if (me.tag() == null || !Tags.normalize(me.tag()).equals(Tags.normalize(playerTag))
                || me.cards() == null || me.cards().size() != DECK_SIZE) {
            return Optional.empty();
        }
        return Optional.of(me.cards());
    }

    private static boolean containsAll(List<BattleLogEntry.Card> battleDeck, List<BattleLogEntry.Card> deck) {
        Set<Integer> ids = battleDeck.stream().map(BattleLogEntry.Card::id).collect(Collectors.toSet());
        return deck.stream().allMatch(card -> ids.contains(card.id()));
    }

    /**
     * 抜けているカードを、対戦で置いていたのと同じ位置に差し込む。
     * 並び順はゲーム内のスロット順(先頭が進化・ヒーロー枠)でコピー用リンクにも使うので、今のデッキの順を崩さない。
     * レベルは今のデッキの値を残す(対戦時より上がっていることがあるため)。
     */
    private static List<BattleLogEntry.Card> fill(List<BattleLogEntry.Card> deck,
            List<BattleLogEntry.Card> battleDeck) {
        Set<Integer> present = deck.stream().map(BattleLogEntry.Card::id).collect(Collectors.toSet());
        List<BattleLogEntry.Card> filled = new ArrayList<>(deck);
        for (int i = 0; i < battleDeck.size(); i++) {
            BattleLogEntry.Card card = battleDeck.get(i);
            if (!present.contains(card.id())) {
                filled.add(Math.min(i, filled.size()), card);
            }
        }
        return List.copyOf(filled);
    }
}
