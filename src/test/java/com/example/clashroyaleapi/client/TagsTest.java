package com.example.clashroyaleapi.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagsTest {

    @Test
    void 先頭のシャープを補って大文字に揃える() {
        assertEquals("#2ABC123", Tags.normalize("2abc123"));
        assertEquals("#2ABC123", Tags.normalize("#2ABC123"));
    }

    @Test
    void コピペで混入した前後の空白を取り除く() {
        assertEquals("#2ABC123", Tags.normalize("  #2ABC123 "));
    }

    @Test
    void クラロワのタグに存在しない英字Oは数字のゼロとして扱う() {
        assertEquals("#2Y0LQ", Tags.normalize("2YOLQ"));
    }

    @Test
    void パスに載せる際はシャープを除いた表記を返す() {
        assertEquals("2ABC123", Tags.toPathSegment("#2abc123"));
    }

    @Test
    void 英数字のみならタグとみなす() {
        assertTrue(Tags.looksLikeTag("2ABC123"));
        assertTrue(Tags.looksLikeTag("#2abc123"));
    }

    @Test
    void 日本語のクラン名や短すぎる文字列はタグとみなさない() {
        // タグ検索を空振りさせずに、いきなりクラン名検索へ回すための判定。
        assertFalse(Tags.looksLikeTag("償い"));
        assertFalse(Tags.looksLikeTag("クラロワ最強クラン"));
        assertFalse(Tags.looksLikeTag("AB"));
        assertFalse(Tags.looksLikeTag(""));
    }

    @Test
    void タグに使われる文字だけでできているかを判定する() {
        // プレイヤー検索で、名前にしか見えない入力を公式APIに問い合わせずに名前検索へ回すための判定。
        assertTrue(Tags.usesOnlyTagCharacters("#2pylqgrjcuv"));
        assertTrue(Tags.usesOnlyTagCharacters("POG"));
        assertFalse(Tags.usesOnlyTagCharacters("bob"));
        assertFalse(Tags.usesOnlyTagCharacters("2ABC123"));
        assertFalse(Tags.usesOnlyTagCharacters(null));
    }

    @Test
    void 全角で打ったタグは半角にして読む() {
        assertEquals("#2VG8GV9UV", Tags.playerTagInput("＃２ＶＧ８ＧＶ９ＵＶ"));
        assertEquals("2vg8", Tags.playerTagInput("２ｖｇ８"));
        assertEquals("#2VUJ00YG", Tags.clanTagInput("＃２ＶＵＪ００ＹＧ"));
    }

    @Test
    void 半角にしてもタグの形にならない入力は名前としてそのまま残す() {
        assertEquals("＃てち", Tags.playerTagInput("＃てち"));
        assertEquals("ﾏﾖﾈｰｽﾞ", Tags.playerTagInput("ﾏﾖﾈｰｽﾞ"));
        assertEquals("償い", Tags.clanTagInput("償い"));
    }

    @Test
    void プロフィールの共有リンクからタグを取り出す() {
        assertEquals("#2VG8GV9UV",
                Tags.playerTagInput("https://link.clashroyale.com/invite/friend/ja?tag=2VG8GV9UV&token=abc12345&platform=iOS"));
        // 共有の文面ごと貼られたとき・タグが先頭以外の引数のとき・「#」がエンコードされているとき
        assertEquals("#2VG8GV9UV", Tags.playerTagInput(
                "クラロワで友達になろう! https://link.clashroyale.com/invite/friend/en?token=abc&tag=2VG8GV9UV&platform=android"));
        assertEquals("#2VG8GV9UV", Tags.playerTagInput("link.clashroyale.com/invite/friend/ja?tag=%232VG8GV9UV"));
    }

    @Test
    void 招待リンクはプレイヤーとクランを取り違えない() {
        String clanLink = "https://link.clashroyale.com/invite/clan/ja?tag=QYCCGRGC&token=r6em68dp&platform=iOS";
        assertEquals("#QYCCGRGC", Tags.clanTagInput(clanLink));
        assertEquals(clanLink, Tags.playerTagInput(clanLink));
    }

    @Test
    void nullでも例外にならない() {
        assertEquals("#", Tags.normalize(null));
        assertFalse(Tags.looksLikeTag(null));
    }
}
