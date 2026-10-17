package com.example.clashroyaleapi.web.view;

import java.util.List;

/**
 * クラン情報画面の「クラン対戦の参加状況」。battleDay が false の日(攻撃の無い日)は、表の代わりに案内文を出す。
 * members は今週1回以上攻撃した人(表に出す)、notAttackedThisWeek は今週まだ1回も攻撃していない人(名前だけ並べる)。
 */
public record ClanWarView(boolean battleDay, int membersBattledToday, int memberCount, int decksUsedToday,
        int maxDecksToday, int decksPerDay, List<MemberView> members, List<MemberView> notAttackedThisWeek) {

    /** notBattledToday の行は、今日まだ攻撃していない人として目立たせる。 */
    public record MemberView(String pathTag, String name, int decksUsedToday, int decksUsedThisWeek,
            boolean notBattledToday) {
    }
}
