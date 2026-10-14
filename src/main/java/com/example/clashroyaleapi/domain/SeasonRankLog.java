package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.Tags;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 1つのシーズンの、世界上位1000人それぞれの順位とレーティングの推移(プレイヤー情報画面のグラフ)。
 * 15分ごとのランキングの記録を、1時間に1つ(その時間の最後の記録)に間引いて持つ。1シーズン(約1か月)で約72万点。
 * 記録のバッチが足し、画面のリクエストが読むので、メソッドは同期する。
 */
public final class SeasonRankLog {

    public record Point(Instant at, int rank, int rating) {
    }

    private final String season;
    private final Map<String, List<Point>> byTag = new HashMap<>();
    private Instant lastAt;
    private Set<String> lastTags = Set.of();

    /** @param season 記録の finishedSeason(終わったシーズンのうち最新のもの)。これが同じ記録を同じシーズンとみなす */
    public SeasonRankLog(String season) {
        this.season = season;
    }

    /**
     * 記録を足す。最後に足した記録と同じ時間(UTC)なら、前の記録の分を置き換える。
     * 最後に足した記録より古い記録は無視する。
     *
     * @return 違うシーズンの記録なら、足さずに false
     */
    public synchronized boolean add(RankingSnapshot snapshot) {
        if (!Objects.equals(season, snapshot.finishedSeason())) {
            return false;
        }
        Instant at = snapshot.takenAt();
        if (lastAt != null && !at.isAfter(lastAt)) {
            return true;
        }
        if (lastAt != null && lastAt.truncatedTo(ChronoUnit.HOURS).equals(at.truncatedTo(ChronoUnit.HOURS))) {
            for (String tag : lastTags) {
                List<Point> points = byTag.get(tag);
                points.removeLast();
                if (points.isEmpty()) {
                    byTag.remove(tag);
                }
            }
        }
        Set<String> tags = new HashSet<>();
        for (RankingSnapshot.Entry entry : snapshot.entries()) {
            String tag = Tags.normalize(entry.tag());
            if (tags.add(tag)) {
                byTag.computeIfAbsent(tag, key -> new ArrayList<>()).add(new Point(at, entry.rank(), entry.rating()));
            }
        }
        lastAt = at;
        lastTags = tags;
        return true;
    }

    /** そのプレイヤーの推移(古い順)。一度も上位に入っていなければ空。 */
    public synchronized List<Point> of(String tag) {
        return List.copyOf(byTag.getOrDefault(Tags.normalize(tag), List.of()));
    }

    /** 最後に足した記録の日時。まだ足していなければ null。 */
    public synchronized Instant lastAt() {
        return lastAt;
    }

    public String season() {
        return season;
    }
}
