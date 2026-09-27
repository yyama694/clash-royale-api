package com.example.clashroyaleapi.client.exception;

/**
 * 指定したタグのプレイヤー・クラン・対戦が存在しない。
 * タグとして不正な形式の場合、公式APIは404ではなく400を返すため、400もこの型に寄せている。
 */
public class ResourceNotFoundException extends ClashRoyaleApiException {

    // 何を探していたかが分かるときは、画面の文言もそれに合わせる(「プレイヤー・クラン」とまとめると、どちらのタグを確かめればよいか伝わらない)。
    public static final String ANY = "error.notFound";
    public static final String PLAYER = "error.playerNotFound";
    public static final String CLAN = "error.clanNotFound";

    public ResourceNotFoundException(String detail, Throwable cause) {
        this(ANY, detail, cause);
    }

    public ResourceNotFoundException(String messageKey, String detail, Throwable cause) {
        super(messageKey, detail, cause);
    }
}
