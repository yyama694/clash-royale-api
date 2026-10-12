package com.example.clashroyaleapi.web.view;

/**
 * 名前検索の候補1人分。lastSeen は当サイトがその名前を最後に確認した日時(不明なら null)。
 * profile は今のトロフィーなど(候補が1人だけのときと、先頭の人数から外れたときは null)。
 */
public record PlayerNameMatchView(String name, String tag, String pathTag, TimeView lastSeen,
        PlayerProfileView profile) {
}
