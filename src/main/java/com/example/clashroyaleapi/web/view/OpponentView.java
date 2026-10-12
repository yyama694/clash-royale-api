package com.example.clashroyaleapi.web.view;

import java.util.List;

/**
 * 対戦履歴一覧の対戦相手。averageLevel はデッキの平均カードレベルで、求められないときは null。
 * cards は相手のデッキ(公式APIが返さなければ空)。1戦ずつ対戦詳細を開かなくても、何と当たったかが分かるように出す。
 */
public record OpponentView(String name, String pathTag, Double averageLevel, List<CardView> cards) {
}
