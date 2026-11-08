package com.example.clashroyaleapi.web.view;

import java.util.List;

/**
 * クラロワクイズの今日の問題。date はUTCの日付(yyyy-MM-dd)で、ブラウザに保存する成績の日付にも使う。
 * 問題は30問すべてをページに埋め、進行・採点・保存はブラウザのJSで行う。sizes は選べる問題数(先頭から何問出すか)。
 * leads はその日の問題の種類の説明(種類ごとに1行)。
 */
public record QuizView(String date, int number, String roundLabel, List<String> leads, List<Integer> sizes,
        List<Question> questions) {

    /**
     * 1問分。type は見せ方の切り替えに使う(elixir: カード1枚とエリクサーの4択、hp: 4枚をヒットポイントの多い順に並べる、
     * spell: 呪文1発で倒せる候補をすべて選ぶ)。
     * key は保存した回の出題が今日の問題と同じかを確かめるためのもの。
     * answer は正解で、エリクサーは choices での位置、並べ替えは cards での位置を正しい順に並べたもの("2031")、
     * 呪文は倒せる候補の cards での位置を小さい順に並べたもの("13")。
     * 正解がページのソースに入るが、成績はその人のブラウザにしか残らないので、調べて答えられても実害は小さい。
     * note は答えた後に添える一言。無ければ null。subject は呪文の問題の呪文(ほかの種類では null)。
     */
    public record Question(String key, String type, String prompt, String note, List<Card> cards,
            List<String> choices, String answer, Card subject) {

        public String choicesText() {
            return String.join(",", choices);
        }
    }

    /**
     * value は並べ替えと呪文の問題で答えた後に出す値(ヒットポイント)。condition はその値がどの姿の値かの注釈で、
     * 問題を出すときからカード名の下に出す(「1体あたり」「草のヒットポイント」など)。
     * どちらもエリクサーの問題と、注釈の要らないカードでは null。スキルのコストの問題では condition がスキル名で、
     * 結果の「間違えた問題」にだけ出す(問題の画面ではスキル名を問題文で言う)。
     */
    public record Card(int id, String name, String iconUrl, String value, String condition) {
    }
}
