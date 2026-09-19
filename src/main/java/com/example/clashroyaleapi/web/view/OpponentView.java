package com.example.clashroyaleapi.web.view;

/** 対戦履歴一覧の対戦相手。averageLevel はデッキの平均カードレベルで、求められないときは null。 */
public record OpponentView(String name, String pathTag, Double averageLevel) {
}
