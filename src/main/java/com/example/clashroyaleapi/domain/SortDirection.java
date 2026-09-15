package com.example.clashroyaleapi.domain;

import java.util.Locale;

public enum SortDirection {

    ASC,
    DESC;

    public static SortDirection from(String value) {
        return "desc".equalsIgnoreCase(value) ? DESC : ASC;
    }

    public SortDirection reversed() {
        return this == ASC ? DESC : ASC;
    }

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
