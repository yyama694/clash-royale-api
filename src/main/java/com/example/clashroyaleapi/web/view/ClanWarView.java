package com.example.clashroyaleapi.web.view;

import java.util.List;

/** クラン情報画面の「クラン対戦の参加状況」。battleDay が false の日(攻撃の無い日)は、表の代わりに案内文を出す。 */
public record ClanWarView(boolean battleDay, int membersBattledToday, int memberCount, int decksUsedToday,
        int maxDecksToday, int decksPerDay, List<MemberView> members) {

    /** notBattledToday の行は、今日まだ攻撃していない人として目立たせる。 */
    public record MemberView(String pathTag, String name, int decksUsedToday, int decksUsedThisWeek,
            boolean notBattledToday) {
    }
}
