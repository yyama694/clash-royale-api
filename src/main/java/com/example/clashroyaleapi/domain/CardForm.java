package com.example.clashroyaleapi.domain;

/**
 * 対戦でカードをどの形で使ったか。進化は日本語のゲーム内表記では「限界突破」。
 * 公式APIの対戦のカードの evolutionLevel は、実際にその形で使ったときだけ付き、1が進化、2がヒーロー
 * (世界上位40人の対戦2,456デッキで確認。付くのはデッキの先頭3枠だけ。2026-10-03)。
 * プレイヤー情報の currentDeck の evolutionLevel は意味が違い、持っている形(1=進化、2=ヒーロー、3=両方)を表す。
 * 枠に置いていないカードにも付くため、使用中のデッキの形は対戦から取る(CurrentDeck 参照)。
 */
public enum CardForm {
    NORMAL, EVOLUTION, HERO;

    public static CardForm ofBattle(Integer evolutionLevel) {
        if (evolutionLevel == null) {
            return NORMAL;
        }
        return switch (evolutionLevel) {
            case 1 -> EVOLUTION;
            case 2 -> HERO;
            default -> NORMAL;
        };
    }

    /** 集計ファイルに残すときの値。対戦の evolutionLevel と同じ数にしておく。 */
    public int battleLevel() {
        return switch (this) {
            case NORMAL -> 0;
            case EVOLUTION -> 1;
            case HERO -> 2;
        };
    }
}
