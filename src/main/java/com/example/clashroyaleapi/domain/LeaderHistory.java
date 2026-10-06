package com.example.clashroyaleapi.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 記録ごとの世界1位の並び。首位交代と、ある時点の1位がいつから続けて1位かを求める。
 * 記録は一定間隔(15分)なので、記録と記録の間に入れ替わって元に戻った首位交代は数えられない。
 * シーズンが違う記録どうしは比べない(新しいシーズンの最初の1位を「首位交代」と数えないため)。
 */
public final class LeaderHistory {

    public record Point(Instant at, String finishedSeason, RankingSnapshot.Entry leader) {
    }

    /** @param at 交代を見つけた記録の日時(交代はその記録と1つ前の記録の間に起きている) */
    public record Change(Instant at, RankingSnapshot.Entry leader, RankingSnapshot.Entry previous) {
    }

    /**
     * @param sinceFirstRecord そのシーズンの最初の記録からずっと1位で、本当はもっと前から1位だった可能性がある
     */
    public record Reign(RankingSnapshot.Entry leader, Instant since, boolean sinceFirstRecord) {
    }

    private final List<Point> points;

    public LeaderHistory(List<Point> points) {
        this.points = points.stream().sorted(Comparator.comparing(Point::at)).toList();
    }

    /** from から to まで(両端を含む)に見つけた首位交代を、新しい順に返す。 */
    public List<Change> changes(Instant from, Instant to) {
        List<Change> changes = new ArrayList<>();
        for (int i = points.size() - 1; i > 0; i--) {
            Point current = points.get(i);
            Point previous = points.get(i - 1);
            if (current.at().isBefore(from) || current.at().isAfter(to)) {
                continue;
            }
            if (sameSeason(previous, current) && !sameLeader(previous, current)) {
                changes.add(new Change(current.at(), current.leader(), previous.leader()));
            }
        }
        return changes;
    }

    /** at の時点(それ以前で最も新しい記録)の1位が、いつから続けて1位か。at 以前に記録が無ければ空。 */
    public Optional<Reign> reignAt(Instant at) {
        int last = -1;
        for (int i = 0; i < points.size() && !points.get(i).at().isAfter(at); i++) {
            last = i;
        }
        if (last < 0) {
            return Optional.empty();
        }
        int first = last;
        while (first > 0 && sameSeason(points.get(first - 1), points.get(last))
                && sameLeader(points.get(first - 1), points.get(last))) {
            first--;
        }
        boolean sinceFirstRecord = first == 0 || !sameSeason(points.get(first - 1), points.get(first));
        return Optional.of(new Reign(points.get(last).leader(), points.get(first).at(), sinceFirstRecord));
    }

    private static boolean sameSeason(Point a, Point b) {
        return Objects.equals(a.finishedSeason(), b.finishedSeason());
    }

    private static boolean sameLeader(Point a, Point b) {
        return a.leader().tag().equals(b.leader().tag());
    }
}
