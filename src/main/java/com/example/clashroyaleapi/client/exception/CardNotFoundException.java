package com.example.clashroyaleapi.client.exception;

/** 指定したIDのカードが公式APIのカード一覧に無い。 */
public class CardNotFoundException extends ClashRoyaleApiException {

    public CardNotFoundException(String detail) {
        super("error.cardNotFound", detail, null);
    }
}
