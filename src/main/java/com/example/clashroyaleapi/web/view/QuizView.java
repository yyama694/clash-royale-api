package com.example.clashroyaleapi.web.view;

import java.util.List;

/**
 * クラロワクイズの今日の問題。date はUTCの日付(yyyy-MM-dd)で、ブラウザに保存する成績の日付にも使う。
 * 問題は30問すべてをページに埋め、進行・採点・保存はブラウザのJSで行う。sizes は選べる問題数(先頭から何問出すか)。
 * lead はその日の問題の種類の説明。
 */
public record QuizView(String date, int number, String roundLabel, String lead, List<Integer> sizes,
        List<Question> questions) {

    /**
     * 1問分。type は選択肢の見せ方(エリクサーなら水滴)の切り替えに使う。answer は正解の choices での位置。
     * 正解がページのソースに入るが、成績はその人のブラウザにしか残らないので、調べて答えられても実害は小さい。
     * note は答えた後に添える一言(地上に出すと別のコストになるカードの、もう一方の形のコスト)。無ければ null。
     */
    public record Question(int cardId, String name, String iconUrl, String type, String prompt, String note,
            List<String> choices, int answer) {

        public String choicesText() {
            return String.join(",", choices);
        }
    }
}
