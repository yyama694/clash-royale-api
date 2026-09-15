package com.example.clashroyaleapi.client.exception;

/**
 * battleTimeで指定された対戦が対戦履歴に無い。
 * 対戦履歴は新しい対戦が入るたびに古いものが押し出されるため、時間が経ったリンクは自然にここへ来る。
 */
public class BattleNotFoundException extends ClashRoyaleApiException {

    public BattleNotFoundException(String detail) {
        super("error.battleNotFound", detail, null);
    }
}
