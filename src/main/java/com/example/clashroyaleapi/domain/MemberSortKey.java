package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.dto.ClanResponse;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

/** クランメンバー一覧のソート対象。不正な値をURLで渡されても列挙にない限り無視される。 */
public enum MemberSortKey {

    NAME("name", Comparator.comparing(ClanResponse.Member::name, String.CASE_INSENSITIVE_ORDER)),
    ROLE("role", Comparator.comparingInt(member -> ClanRole.rankOf(member.role()))),
    TROPHIES("trophies", Comparator.comparingInt(ClanResponse.Member::trophies)),
    DONATIONS("donations", Comparator.comparingInt(ClanResponse.Member::donations)),
    // 非アクティブ日数の昇順(最近アクセスした人が先)。lastSeenの文字列は時刻順に辞書式で並ぶため、
    // 文字列を降順にすると日数の昇順になる。取得できなかったメンバーは末尾に置く。
    LAST_SEEN("lastSeen", Comparator.comparing(ClanResponse.Member::lastSeen,
            Comparator.nullsLast(Comparator.<String>reverseOrder())));

    private final String code;
    private final Comparator<ClanResponse.Member> ascending;

    MemberSortKey(String code, Comparator<ClanResponse.Member> ascending) {
        this.code = code;
        this.ascending = ascending;
    }

    public static Optional<MemberSortKey> from(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(key -> key.code.equalsIgnoreCase(value)).findFirst();
    }

    /** URLのクエリに載せる表記。テンプレートのソートリンク生成に使う。 */
    public String code() {
        return code;
    }

    public Comparator<ClanResponse.Member> comparator(SortDirection direction) {
        return direction == SortDirection.DESC ? ascending.reversed() : ascending;
    }
}
