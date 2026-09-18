package com.example.clashroyaleapi.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardOriginTest {

    @Test
    void 対戦詳細から開いたら対戦詳細に戻る() {
        CardOrigin origin = CardOrigin.fromQuery("VLL8YQC9G", "20260918T201315.000Z");
        assertEquals("/player/VLL8YQC9G/battles/20260918T201315.000Z", origin.backPath());
        assertEquals("action.backToBattle", origin.backLabelKey());
    }

    @Test
    void プレイヤー情報から開いたらプレイヤー情報に戻る() {
        CardOrigin origin = CardOrigin.fromQuery("VLL8YQC9G", null);
        assertEquals("/player/VLL8YQC9G", origin.backPath());
        assertEquals("action.backToPlayer", origin.backLabelKey());
    }

    @Test
    void カードへのリンクには分かっている項目だけを付ける() {
        assertEquals("/card/26000000?player=VLL8YQC9G&battle=20260918T201315.000Z",
                CardOrigin.battle("VLL8YQC9G", "20260918T201315.000Z").cardPath(26000000));
        assertEquals("/card/26000000?player=VLL8YQC9G", CardOrigin.player("VLL8YQC9G").cardPath(26000000));
        assertEquals("/card/26000000", CardOrigin.none().cardPath(26000000));
    }

    @Test
    void 指定が無ければトップに戻る() {
        CardOrigin origin = CardOrigin.fromQuery(null, null);
        assertEquals("/", origin.backPath());
        assertEquals("action.backToTop", origin.backLabelKey());
    }

    @Test
    void タグとして成立しない値はトップに戻す() {
        // 外部サイトへの誘導やパスの書き換えに使われないよう、タグの形式に合わない値は受け付けない。
        assertEquals("/", CardOrigin.fromQuery("//evil.example.com", null).backPath());
        assertEquals("/", CardOrigin.fromQuery("../clan/ABC", null).backPath());
    }

    @Test
    void 対戦日時の形式が違えばプレイヤー情報に戻す() {
        assertEquals("/player/VLL8YQC9G", CardOrigin.fromQuery("VLL8YQC9G", "../../ranking").backPath());
    }

    @Test
    void タグは正規化してから使う() {
        assertEquals("/player/VLL8YQC9G", CardOrigin.fromQuery("#vll8yqc9g", null).backPath());
    }
}
