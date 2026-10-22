package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.Tags;

import java.time.Duration;
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
 * <p>
 * 公式のランキングは一斉に作り直されることがあり(2026-10-15に上位1000人のうち約630人が一度に消え、
 * 下の人が繰り上がった。消えた人は次に対戦したときから順に戻る)、その間の順位は実際より良く出るので使わない。
 * ふだん1000位のレーティングは15分で2までしか下がらないので、20より大きく下がった記録(1000人に満たない記録を含む)
 * から、1000位のレーティングが下がる前の値に戻るまでを捨てる(10/15は約9時間半)。
 */
public final class SeasonRankLog {

    /** @param ranked その時点でランキングに載っていた人数。シーズン開始直後で1000人に満たない間だけ1000より少ない */
    public record Point(Instant at, int rank, int rating, int ranked) {

        public boolean rankingIncomplete() {
            return ranked < FULL_SIZE;
        }
    }

    /** 記録は公式APIが返せる上限の1000人まで取る(RankingService.MAX_PLAYER_RANKING_SIZE) */
    static final int FULL_SIZE = 1000;
    static final int FLOOR_DROP_LIMIT = 20;
    /** 作り直しの前の水準に戻らないまま(シーズン途中でレーティングがリセットされたなど)、グラフが止まり続けないように */
    static final Duration MAX_DISTORTION = Duration.ofHours(24);

    private final String season;
    private final Map<String, List<Point>> byTag = new HashMap<>();
    private Instant lastAt;
    private Instant lastAddedAt;
    private Set<String> lastTags = Set.of();
    private Integer lastFloor;
    private Integer recoveryFloor;
    private Instant distortedSince;

    /** @param season 記録の finishedSeason(終わったシーズンのうち最新のもの)。これが同じ記録を同じシーズンとみなす */
    public SeasonRankLog(String season) {
        this.season = season;
    }

    /**
     * 記録を足す。最後に足した記録と同じ時間(UTC)なら、前の記録の分を置き換える。
     * 最後に受け取った記録より古い記録は無視する。公式のランキングの作り直しで崩れている記録は、受け取るが足さない。
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
        lastAt = at;
        Integer floor = snapshot.entries().size() >= FULL_SIZE ? snapshot.entries().getLast().rating() : null;
        if (recoveryFloor == null && lastFloor != null && (floor == null || floor < lastFloor - FLOOR_DROP_LIMIT)) {
            recoveryFloor = lastFloor;
            distortedSince = at;
        }
        if (recoveryFloor != null) {
            boolean recovered = floor != null && floor >= recoveryFloor;
            if (!recovered && at.isBefore(distortedSince.plus(MAX_DISTORTION))) {
                return true;
            }
            recoveryFloor = null;
            distortedSince = null;
        }
        lastFloor = floor;

        if (lastAddedAt != null && lastAddedAt.truncatedTo(ChronoUnit.HOURS).equals(at.truncatedTo(ChronoUnit.HOURS))) {
            for (String tag : lastTags) {
                List<Point> points = byTag.get(tag);
                points.removeLast();
                if (points.isEmpty()) {
                    byTag.remove(tag);
                }
            }
        }
        int ranked = snapshot.entries().size();
        Set<String> tags = new HashSet<>();
        for (RankingSnapshot.Entry entry : snapshot.entries()) {
            String tag = Tags.normalize(entry.tag());
            if (tags.add(tag)) {
                byTag.computeIfAbsent(tag, key -> new ArrayList<>())
                        .add(new Point(at, entry.rank(), entry.rating(), ranked));
            }
        }
        lastAddedAt = at;
        lastTags = tags;
        return true;
    }

    /** そのプレイヤーの推移(古い順)。一度も上位に入っていなければ空。 */
    public synchronized List<Point> of(String tag) {
        return List.copyOf(byTag.getOrDefault(Tags.normalize(tag), List.of()));
    }

    /** 最後に受け取った記録の日時(作り直しで崩れていて足さなかった記録を含む)。まだ受け取っていなければ null。 */
    public synchronized Instant lastAt() {
        return lastAt;
    }

    public String season() {
        return season;
    }
}
