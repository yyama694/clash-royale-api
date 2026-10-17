package com.example.clashroyaleapi.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * boatBattleSide は船のバトル(type が boatBattle)でだけ入り、"attacker" か "defender"。ほかの対戦では null。
 * leagueNumber はランク戦のリーグ({@link com.example.clashroyaleapi.domain.RankedLeague})。ほかの対戦にも1などが入るが意味は無い。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BattleLogEntry(
        String type,
        String battleTime,
        GameMode gameMode,
        List<Participant> team,
        List<Participant> opponent,
        String boatBattleSide,
        Integer leagueNumber
) {

    public BattleLogEntry(String type, String battleTime, GameMode gameMode, List<Participant> team,
            List<Participant> opponent, String boatBattleSide) {
        this(type, battleTime, gameMode, team, opponent, boatBattleSide, null);
    }

    /**
     * trophyChange は入らない対戦がある(フレンドバトル・クラン対戦、ランク戦の一部など)ので null になりうる。
     * elixirLeaked はエリクサーが満タンのまま使えずに無駄になった量(ゲーム内の観戦・リプレイ画面の「消費漏れ」
     * TID_SPECTATE_ELIXIR_LEAKED)。2026-10-18に確かめた約3,000戦では全員に入っていたが、念のため null を許す。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Participant(String tag, String name, int crowns, List<Card> cards, List<Card> supportCards,
            Integer trophyChange, Double elixirLeaked) {

        public Participant(String tag, String name, int crowns, List<Card> cards, List<Card> supportCards,
                Integer trophyChange) {
            this(tag, name, crowns, cards, supportCards, trophyChange, null);
        }
    }

    /**
     * levelはレアリティごとに1から数え直した値。ゲーム内表記に直すにはmaxLevelが要る(CardLevel参照)。
     * evolutionLevel は対戦とプレイヤー情報の currentDeck で意味が違う(CardForm 参照)。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Card(int id, String name, int level, int maxLevel, Integer elixirCost, Integer evolutionLevel,
            IconUrls iconUrls) {

        /** 画像URLが返らないカードもあるため、無ければ null。 */
        public String mediumIconUrl() {
            return iconUrls != null ? iconUrls.medium() : null;
        }

        public Card withEvolutionLevel(Integer evolutionLevel) {
            return new Card(id, name, level, maxLevel, elixirCost, evolutionLevel, iconUrls);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IconUrls(String medium, String evolutionMedium, String heroMedium) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GameMode(String name) {
    }
}
