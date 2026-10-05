package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageSliceTest {

    private static final List<Integer> RANKS = IntStream.rangeClosed(1, 250).boxed().toList();

    @Test
    void 決まった件数ずつに分け_最後のページは残りだけ() {
        PageSlice<Integer> second = PageSlice.of(RANKS, 2, 100);
        PageSlice<Integer> last = PageSlice.of(RANKS, 3, 100);

        assertEquals(101, second.items().get(0));
        assertEquals(100, second.items().size());
        assertEquals(3, second.pageCount());
        assertEquals(List.of(201, 250), List.of(last.items().get(0), last.items().get(last.items().size() - 1)));
        assertEquals(250, last.total());
    }

    @Test
    void 範囲外のページ番号は最初か最後に寄せる() {
        assertEquals(1, PageSlice.of(RANKS, 0, 100).page());
        assertEquals(3, PageSlice.of(RANKS, 99, 100).page());
        assertEquals(50, PageSlice.of(RANKS, 99, 100).items().size());
    }

    @Test
    void 空の一覧は1ページで中身が空() {
        PageSlice<Integer> empty = PageSlice.of(List.of(), 3, 100);

        assertEquals(1, empty.page());
        assertEquals(1, empty.pageCount());
        assertEquals(List.of(), empty.items());
    }
}
