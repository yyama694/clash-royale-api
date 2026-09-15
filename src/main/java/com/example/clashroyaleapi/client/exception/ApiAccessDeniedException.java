package com.example.clashroyaleapi.client.exception;

/**
 * APIキーが拒否された(403)。公式APIはアクセス元IPのホワイトリスト方式のため、
 * 開発機のグローバルIPが変わった場合もここに来る。
 */
public class ApiAccessDeniedException extends ClashRoyaleApiException {

    public ApiAccessDeniedException(String detail, Throwable cause) {
        super("error.accessDenied", detail, cause);
    }
}
