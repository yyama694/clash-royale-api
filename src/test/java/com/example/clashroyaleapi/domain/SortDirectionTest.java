package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SortDirectionTest {

    @Test
    void descだけを降順とし大文字小文字は区別しない() {
        assertEquals(SortDirection.DESC, SortDirection.from("desc"));
        assertEquals(SortDirection.DESC, SortDirection.from("DESC"));
    }

    @Test
    void 不正な値や未指定は昇順にする() {
        assertEquals(SortDirection.ASC, SortDirection.from("asc"));
        assertEquals(SortDirection.ASC, SortDirection.from(null));
        assertEquals(SortDirection.ASC, SortDirection.from("descending"));
    }

    @Test
    void reversedとcode() {
        assertEquals(SortDirection.DESC, SortDirection.ASC.reversed());
        assertEquals(SortDirection.ASC, SortDirection.DESC.reversed());
        assertEquals("asc", SortDirection.ASC.code());
        assertEquals("desc", SortDirection.DESC.code());
    }
}
