package com.example.clashroyaleapi.client;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * プレイヤータグ・クランタグの正規化と形式判定。
 * 公式APIに渡す前にここで整えることで、コピペ由来の空白や小文字入力による無駄な400応答を防ぐ。
 */
public final class Tags {

    private static final Pattern TAG_BODY = Pattern.compile("[0-9A-Z]{3,15}");
    // 実際のタグに使われる文字だけ(公式のタグは 0289PYLQGRJCUV の14文字で作られる)。
    private static final Pattern STRICT_TAG_BODY = Pattern.compile("[0289PYLQGRJCUV]{3,15}");

    private Tags() {
    }

    /**
     * 前後空白・先頭の "#"・小文字を吸収して "#XXXXX" 形式に揃える。
     * クラロワのタグに英字の O は存在しないため、0 の打ち間違いとみなして変換する。
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "#";
        }
        String body = raw.strip();
        if (body.startsWith("#")) {
            body = body.substring(1);
        }
        return "#" + body.toUpperCase(Locale.ROOT).replace('O', '0');
    }

    /** "#" を除いた表記。URLのパスに載せるときに使う。 */
    public static String toPathSegment(String raw) {
        return normalize(raw).substring(1);
    }

    /**
     * タグとして成立する形式か。クラン検索で「タグ検索」と「クラン名検索」を振り分けるために使う。
     * 判定を誤ってもタグ検索が空振りすればクラン名検索にフォールバックするため、緩めの判定にしている。
     */
    public static boolean looksLikeTag(String raw) {
        return TAG_BODY.matcher(normalize(raw).substring(1)).matches();
    }

    /**
     * タグに使われる文字だけでできているか。プレイヤー検索で、名前として探すべき入力(「bob」など)を
     * 公式APIに問い合わせずに名前検索へ回すために使う。
     */
    public static boolean usesOnlyTagCharacters(String raw) {
        return STRICT_TAG_BODY.matcher(normalize(raw).substring(1)).matches();
    }
}
