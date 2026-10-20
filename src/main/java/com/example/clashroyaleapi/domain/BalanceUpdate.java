package com.example.clashroyaleapi.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 1回分のバランス調整(公式のリリースノートの内容)。中身は {@code balance/changes.tsv} に手で写す。
 *
 * @param changes 公式の記事の順(弱体化→強化→調整→不具合の修正)
 */
public record BalanceUpdate(LocalDate date, String sourceUrl, List<Change> changes) {

    /** 並び順は公式の記事の見出しの順。 */
    public enum Kind {
        NERF, BUFF, REWORK, FIX;

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * @param card    公式APIの英語のカード名
     * @param stat    項目(messages の balance.stat.&lt;項目&gt;)
     * @param before  変更前の値。数値の無い変更(「出現時の広がりを狭める」など)は null。末尾の s は秒、tiles はマス
     * @param percent 公式の記事の変化率。記事に無ければ null
     */
    public record Change(String card, CardForm form, Kind kind, String stat, String before, String after,
            Integer percent) {

        public boolean hasValues() {
            return before != null;
        }
    }

    /** 同じカード・同じ形の変更をまとめたもの(1枚のカードに項目が2つ以上あることがある)。 */
    public record CardChanges(String card, CardForm form, Kind kind, List<Change> changes) {
    }

    /** 種類ごと(公式の見出しの順)に、カード・形ごとにまとめる。カードの順は記事の順のまま。 */
    public List<CardChanges> byCard() {
        List<CardChanges> grouped = new ArrayList<>();
        changes.stream()
                .sorted(Comparator.comparing(Change::kind))
                .forEach(change -> {
                    CardChanges last = grouped.isEmpty() ? null : grouped.getLast();
                    if (last != null && last.card().equals(change.card()) && last.form() == change.form()
                            && last.kind() == change.kind()) {
                        last.changes().add(change);
                    } else {
                        grouped.add(new CardChanges(change.card(), change.form(), change.kind(),
                                new ArrayList<>(List.of(change))));
                    }
                });
        return grouped.stream()
                .map(group -> new CardChanges(group.card(), group.form(), group.kind(), List.copyOf(group.changes())))
                .toList();
    }

    public boolean touches(String card) {
        return changes.stream().anyMatch(change -> change.card().equals(card));
    }

    /**
     * {@code balance/changes.tsv} の行を読む。新しい調整から順に返す。
     * 書式が崩れた行は、気付かずに公開しないよう起動時に失敗させる。
     */
    public static List<BalanceUpdate> parse(List<String> lines) {
        List<BalanceUpdate> updates = new ArrayList<>();
        LocalDate date = null;
        String url = null;
        List<Change> changes = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "update" -> {
                    if (date != null) {
                        updates.add(new BalanceUpdate(date, url, List.copyOf(changes)));
                    }
                    require(f.length == 3, line);
                    date = LocalDate.parse(f[1]);
                    url = f[2];
                    changes = new ArrayList<>();
                }
                case "change" -> {
                    require(date != null && (f.length == 5 || f.length == 7 || f.length == 8), line);
                    boolean values = f.length > 5;
                    changes.add(new Change(f[1], CardForm.valueOf(f[2].toUpperCase(Locale.ROOT)),
                            Kind.valueOf(f[3].toUpperCase(Locale.ROOT)), f[4],
                            values ? f[5] : null, values ? f[6] : null,
                            f.length == 8 ? Integer.valueOf(f[7]) : null));
                }
                default -> require(false, line);
            }
        }
        if (date != null) {
            updates.add(new BalanceUpdate(date, url, List.copyOf(changes)));
        }
        updates.sort(Comparator.comparing(BalanceUpdate::date).reversed());
        return List.copyOf(updates);
    }

    private static void require(boolean ok, String line) {
        if (!ok) {
            throw new IllegalArgumentException("malformed balance change line: " + line);
        }
    }
}
