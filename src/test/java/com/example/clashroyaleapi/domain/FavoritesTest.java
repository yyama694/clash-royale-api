package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FavoritesTest {

    @Test
    void 登録は先頭に積む() {
        Favorites favorites = Favorites.empty().add("AAA", "太郎").add("BBB", "次郎");

        assertEquals(List.of(new Favorites.Entry("BBB", "次郎"), new Favorites.Entry("AAA", "太郎")),
                favorites.entries());
    }

    @Test
    void 上限に達した状態での登録は何もしない() {
        Favorites full = Favorites.empty();
        for (int i = 0; i < Favorites.MAX_ENTRIES; i++) {
            full = full.add("TAG" + i, "name" + i);
        }
        assertTrue(full.isFull());

        Favorites afterEleventh = full.add("EXTRA", "extra");

        assertEquals(Favorites.MAX_ENTRIES, afterEleventh.entries().size());
        assertFalse(afterEleventh.contains("EXTRA"));
    }

    @Test
    void 重複登録は位置を変えない() {
        Favorites favorites = Favorites.empty().add("AAA", "太郎").add("BBB", "次郎");

        Favorites reAdded = favorites.add("AAA", "太郎");

        assertEquals(favorites.entries(), reAdded.entries());
    }

    @Test
    void 解除() {
        Favorites favorites = Favorites.empty().add("AAA", "太郎").add("BBB", "次郎");

        Favorites removed = favorites.remove("AAA");

        assertEquals(List.of(new Favorites.Entry("BBB", "次郎")), removed.entries());
    }

    @Test
    void 未登録のタグの解除は何もしない() {
        Favorites favorites = Favorites.empty().add("AAA", "太郎");

        assertSame(favorites, favorites.remove("ZZZ"));
    }

    @Test
    void 名前の書き換え() {
        Favorites favorites = Favorites.empty().add("AAA", "太郎").add("BBB", "次郎");

        Favorites updated = favorites.updateName("AAA", "太郎(改名)");

        assertEquals(List.of(new Favorites.Entry("BBB", "次郎"), new Favorites.Entry("AAA", "太郎(改名)")),
                updated.entries());
    }

    @Test
    void 名前が変わっていなければそのまま返す() {
        Favorites favorites = Favorites.empty().add("AAA", "太郎");

        assertSame(favorites, favorites.updateName("AAA", "太郎"));
    }

    @Test
    void 未登録のタグへの書き換えは何もしない() {
        Favorites favorites = Favorites.empty().add("AAA", "太郎");

        assertSame(favorites, favorites.updateName("ZZZ", "誰か"));
    }

    @Test
    void ofで11件以上渡すと先頭10件だけになる() {
        List<Favorites.Entry> eleven = IntStream.range(0, 11)
                .mapToObj(i -> new Favorites.Entry("TAG" + i, "name" + i))
                .toList();

        Favorites favorites = Favorites.of(eleven);

        assertEquals(Favorites.MAX_ENTRIES, favorites.entries().size());
        assertEquals("TAG0", favorites.entries().get(0).tag());
    }
}
