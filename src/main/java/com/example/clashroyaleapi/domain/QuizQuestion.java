package com.example.clashroyaleapi.domain;

import java.util.List;

/**
 * クイズの1問。選択肢の値の意味は問題の種類で決まる(エリクサーならコスト)。
 * 画面・保存・共有は種類を知らず、選択肢の並びと正解の位置(answer)だけで動く。
 */
public record QuizQuestion(Type type, int cardId, List<Integer> choices, int answer) {

    public enum Type {
        ELIXIR("elixir");

        private final String code;

        Type(String code) {
            this.code = code;
        }

        /** 文言のキー(quiz.<code>.*)と、画面での選択肢の見せ方の切り替えに使う。 */
        public String code() {
            return code;
        }
    }

    public int correctChoice() {
        return choices.get(answer);
    }
}
