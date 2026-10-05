package com.example.clashroyaleapi.domain;

import java.util.List;

/**
 * 一覧を決まった件数ずつに分けた1ページ分。page は1から数え、total は分ける前の件数。
 * 範囲外のページ番号(URLを手で書き換えたなど)は、エラーにせず最初か最後のページに寄せる。
 */
public record PageSlice<T>(List<T> items, int page, int pageCount, int pageSize, int total) {

    public static <T> PageSlice<T> of(List<T> all, int requestedPage, int pageSize) {
        int pageCount = Math.max(1, (all.size() + pageSize - 1) / pageSize);
        int page = Math.min(Math.max(requestedPage, 1), pageCount);
        int from = (page - 1) * pageSize;
        return new PageSlice<>(all.subList(Math.min(from, all.size()), Math.min(from + pageSize, all.size())),
                page, pageCount, pageSize, all.size());
    }
}
