package com.example.clashroyaleapi.web.view;

import java.util.List;

/**
 * ランキングのページ送り。pages は「1–100」「101–200」…の順位の範囲で、目当ての順位のページへ直接移れるようにする。
 * prevHref・nextHref は最初・最後のページでは null。
 */
public record RankingPagerView(List<PageLink> pages, String prevHref, String nextHref) {

    public record PageLink(String label, String href, boolean current) {
    }
}
