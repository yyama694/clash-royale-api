package com.example.clashroyaleapi.domain;

/** 公式APIの応答で見かけたプレイヤー。名前検索のために、タグと名前の対応を蓄積する単位。 */
public record PlayerSighting(String tag, String name) {
}
