package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.dto.ClanResponse;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;

/** クランメンバー一覧のソート対象。不正な値をURLで渡されても列挙にない限り無視される。 */
public enum MemberSortKey {

    // 装飾タグ付きの名前(<c2>Name)が先頭にまとまらないよう、表示と同じ名前で並べる。
    NAME("name", member -> GameText.stripFormatting(member.name()), String.CASE_INSENSITIVE_ORDER),
    ROLE("role", member -> ClanRole.rankOf(member.role()), Comparator.<Integer>naturalOrder()),
    TROPHIES("trophies", ClanResponse.Member::trophies, Comparator.<Integer>naturalOrder()),
    DONATIONS("donations", ClanResponse.Member::donations, Comparator.<Integer>naturalOrder()),
    // 非アクティブ日数の昇順(最近アクセスした人が先)。lastSeenの文字列は時刻順に辞書式で並ぶため、
    // 文字列を降順にすると日数の昇順になる。
    LAST_SEEN("lastSeen", ClanResponse.Member::lastSeen, Comparator.<String>reverseOrder());

    private final String code;
    private final Comparator<ClanResponse.Member> ascending;
    private final Comparator<ClanResponse.Member> descending;

    // 名前・最終アクセスが取得できなかったメンバーは、並びの向きに関係なく末尾に置く。
    // 昇順の reversed() で降順を作ると nullsLast まで反転して先頭に来るため、向きごとに組み立てる。
    <T> MemberSortKey(String code, Function<ClanResponse.Member, T> key, Comparator<T> order) {
        this.code = code;
        this.ascending = Comparator.comparing(key, Comparator.nullsLast(order));
        this.descending = Comparator.comparing(key, Comparator.nullsLast(order.reversed()));
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
        return direction == SortDirection.DESC ? descending : ascending;
    }
}
