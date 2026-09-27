package com.example.clashroyaleapi.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * お気に入り(プレイヤー・クラン共通)のタグと名前の並び。登録が新しい順で先頭に積む。HTTPやCookieの形式には依存しない。
 */
public final class Favorites {

    public static final int MAX_ENTRIES = 10;

    private final List<Entry> entries;

    private Favorites(List<Entry> entries) {
        this.entries = entries.size() > MAX_ENTRIES ? List.copyOf(entries.subList(0, MAX_ENTRIES)) : List.copyOf(entries);
    }

    public static Favorites empty() {
        return new Favorites(List.of());
    }

    /** Cookieなど外部の並びから作る。11件以上あれば先頭(登録が新しい)10件だけを使う。 */
    public static Favorites of(List<Entry> entries) {
        return new Favorites(entries);
    }

    public List<Entry> entries() {
        return entries;
    }

    public boolean contains(String tag) {
        return entries.stream().anyMatch(e -> e.tag().equals(tag));
    }

    public boolean isFull() {
        return entries.size() >= MAX_ENTRIES;
    }

    /** 登録済みなら(並びは変わらないが)成功扱いにする。未登録で上限に達していれば登録できない。 */
    public boolean canAdd(String tag) {
        return contains(tag) || !isFull();
    }

    /** 既に登録済みなら並び順を変えずそのまま返す。上限に達していて未登録なら、呼び出し側で先にcanAdd()を見て弾く想定で何もしない。 */
    public Favorites add(String tag, String name) {
        if (contains(tag) || isFull()) {
            return this;
        }
        List<Entry> next = new ArrayList<>(entries.size() + 1);
        next.add(new Entry(tag, name));
        next.addAll(entries);
        return new Favorites(next);
    }

    public Favorites remove(String tag) {
        List<Entry> next = entries.stream().filter(e -> !e.tag().equals(tag)).toList();
        return next.size() == entries.size() ? this : new Favorites(next);
    }

    /** 保存してある名前を最新のものに書き直す。該当タグが無い・名前が変わっていなければそのまま返す。 */
    public Favorites updateName(String tag, String name) {
        boolean changed = false;
        List<Entry> next = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            if (entry.tag().equals(tag) && !entry.name().equals(name)) {
                next.add(new Entry(tag, name));
                changed = true;
            } else {
                next.add(entry);
            }
        }
        return changed ? new Favorites(next) : this;
    }

    /** タグ(先頭の"#"なし)と表示名の組。 */
    public record Entry(String tag, String name) {
    }
}
