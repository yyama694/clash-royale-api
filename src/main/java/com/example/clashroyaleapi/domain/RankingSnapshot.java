package com.example.clashroyaleapi.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * ある時点のランク戦の世界ランキング(上位1000人)。ランキングの動き画面のために定期的に記録する。
 *
 * @param finishedSeason 記録した時点で、終わったシーズンのうち最新のもの("2026-09"など。取れなかった記録は null)。
 *                       公式APIは今シーズンのランキングがどのシーズンのものかを返さないので、これが同じ記録どうしを
 *                       同じシーズンとみなす(シーズンが替わるとランキングが空から始まり直し、前後を比べても意味が無いため)
 * @param entries        順位の順
 */
public record RankingSnapshot(Instant takenAt, String finishedSeason, List<Entry> entries) {

    public record Entry(int rank, String tag, String name, int rating) {
    }

    public Optional<Entry> leader() {
        return entries.isEmpty() ? Optional.empty() : Optional.of(entries.getFirst());
    }

    public boolean sameSeason(RankingSnapshot other) {
        return Objects.equals(finishedSeason, other.finishedSeason);
    }

    public Map<String, Entry> byTag() {
        Map<String, Entry> byTag = new LinkedHashMap<>();
        entries.forEach(entry -> byTag.putIfAbsent(entry.tag(), entry));
        return byTag;
    }
}
