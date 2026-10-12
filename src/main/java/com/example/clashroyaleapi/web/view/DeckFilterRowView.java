package com.example.clashroyaleapi.web.view;

import java.util.List;
import java.util.Set;

/**
 * デッキ画面の「カードで絞り込む」の選択欄1つ分。
 *
 * @param cardId      この欄で選んでいるカード。まだ選んでいない欄なら null
 * @param withoutThis この欄のカードを外したときに残るカード(「×」のリンク先)
 * @param otherCards  ほかの欄で選んでいるカード。同じカードを2回選べないよう、この欄の選択肢から除く
 * @param added       「＋」で開く空の欄。1枚も選んでいないときの最初の欄は false
 */
public record DeckFilterRowView(Integer cardId, List<Integer> withoutThis, Set<Integer> otherCards, boolean added) {
}
