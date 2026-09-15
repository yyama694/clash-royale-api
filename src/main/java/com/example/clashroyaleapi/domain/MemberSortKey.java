package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.dto.ClanResponse;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

/** クランメンバー一覧のソート対象。不正な値をURLで渡されても列挙にない限り無視される。 */
public enum MemberSortKey {

    NAME(Comparator.comparing(ClanResponse.Member::name, String.CASE_INSENSITIVE_ORDER)),
    ROLE(Comparator.comparingInt(member -> ClanRole.rankOf(member.role()))),
    TROPHIES(Comparator.comparingInt(ClanResponse.Member::trophies)),
    DONATIONS(Comparator.comparingInt(ClanResponse.Member::donations));

    private final Comparator<ClanResponse.Member> ascending;

    MemberSortKey(Comparator<ClanResponse.Member> ascending) {
        this.ascending = ascending;
    }

    public static Optional<MemberSortKey> from(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(key -> key.name().equals(normalized)).findFirst();
    }

    /** URLのクエリに載せる表記。テンプレートのソートリンク生成に使う。 */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Comparator<ClanResponse.Member> comparator(SortDirection direction) {
        return direction == SortDirection.DESC ? ascending.reversed() : ascending;
    }
}
