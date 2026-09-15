package com.example.clashroyaleapi.client.exception;

/**
 * 指定したタグのプレイヤー・クラン・対戦が存在しない。
 * タグとして不正な形式の場合、公式APIは404ではなく400を返すため、400もこの型に寄せている。
 */
public class ResourceNotFoundException extends ClashRoyaleApiException {

    public ResourceNotFoundException(String detail, Throwable cause) {
        super("error.notFound", detail, cause);
    }
}
