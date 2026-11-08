package com.example.clashroyaleapi.domain;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * クイズの1問。画面・保存・共有は種類を知らず、答えが正解(answer を文字にしたもの)と同じかどうかだけで採点する。
 */
public sealed interface QuizQuestion {

    /** 並びは、はじめの画面で問題の種類の説明を出す順。 */
    enum Type {
        ELIXIR("elixir"), HP_ORDER("hp"), SPELL("spell"), ABILITY("ability");

        private final String code;

        Type(String code) {
            this.code = code;
        }

        /** 文言のキー(quiz.<code>.*)と、画面での見せ方の切り替えに使う。 */
        public String code() {
            return code;
        }
    }

    Type type();

    /**
     * カードのエリクサーを当てる4択。answer は正解の choices での位置。
     * ground はカードの地上の形(スピリットエンプレス（地上）)の問題。
     */
    record Elixir(int cardId, boolean ground, List<Integer> choices, int answer) implements QuizQuestion {

        @Override
        public Type type() {
            return Type.ELIXIR;
        }

        public int correctChoice() {
            return choices.get(answer);
        }
    }

    /**
     * ヒーロー・チャンピオンのスキルの発動に払うエリクサーを当てる4択(2026-11-08にユーザーが決定)。
     * answer は正解の choices での位置。champion はチャンピオンのスキル(画像は通常の姿)、false ならヒーローのスキル(ヒーローの姿)。
     */
    record AbilityCost(int cardId, boolean champion, List<Integer> choices, int answer) implements QuizQuestion {

        @Override
        public Type type() {
            return Type.ABILITY;
        }

        public int correctChoice() {
            return choices.get(answer);
        }
    }

    /** 4枚をヒットポイントの多い順に並べる。cards は最初に見せる並びで、正しい並びにはしない。 */
    record HpOrder(List<HpCard> cards) implements QuizQuestion {

        @Override
        public Type type() {
            return Type.HP_ORDER;
        }

        /** 正しい並び。cards での位置を、ヒットポイントの多い順に並べたもの。 */
        public List<Integer> answer() {
            return IntStream.range(0, cards.size()).boxed()
                    .sorted(Comparator.comparingInt((Integer i) -> cards.get(i).health()).reversed())
                    .toList();
        }
    }

    /**
     * 「呪文一撃で倒せるのはどれ?」。cards は候補(見せる並び)、damages はそれぞれへの呪文のダメージ(レベル16。
     * アースクエイクは建物へのダメージが違う)。倒せるのを全部選び、選んだものが全部合っていれば正解。
     * シールドのあるカードはシールドが外れた後に当たる前提で、ヒットポイントはシールドを含まない値(2026-11-01にユーザーが決定)。
     * 倒せるかどうかはレベル11とレベル16で変わらない(QuizSpellDataTest で確かめている)。
     */
    record SpellKill(int spellId, List<HpCard> cards, List<Integer> damages) implements QuizQuestion {

        @Override
        public Type type() {
            return Type.SPELL;
        }

        /** 倒せる候補の cards での位置(小さい順)。ヒットポイントがダメージと同じなら倒せる。 */
        public List<Integer> answer() {
            return IntStream.range(0, cards.size())
                    .filter(i -> cards.get(i).health() <= damages.get(i))
                    .boxed()
                    .toList();
        }
    }

    /**
     * health はレベル16のヒットポイント。並び順はレベルに関係なく同じ(2026-10-31に全カードで確かめた)。
     * unit はカードとは別に出すユニット(ゴブリンシュタインのモンスターなど。CardUnit)で、カード自体なら null。
     */
    record HpCard(int id, String unit, int health) {
    }
}
