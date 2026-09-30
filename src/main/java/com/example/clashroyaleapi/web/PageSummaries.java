package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.domain.ClanWarParticipation;
import com.example.clashroyaleapi.domain.MemberActivity;
import com.example.clashroyaleapi.domain.PlayerBattleStats;
import com.example.clashroyaleapi.domain.WinLoseStreak;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * プレイヤー情報画面・クラン情報画面の、ページごとのタイトルと要約文。
 * 要約文は検索結果の説明文(meta description・OGP)と、本文の先頭の1段落を兼ねる。
 * 全ページ共通の説明文だと、検索エンジンからは同じ中身のページに見えやすいため(TODO.mdの「ページビュー向上」)。
 */
@Component
public class PageSummaries {

    private final LabelResolver labels;
    private final Clock clock;

    @Autowired
    public PageSummaries(LabelResolver labels) {
        this(labels, Clock.systemUTC());
    }

    PageSummaries(LabelResolver labels, Clock clock) {
        this.labels = labels;
        this.clock = clock;
    }

    public String playerTitle(PlayerResponse player, String playerName, Locale locale) {
        return labels.message("player.seo.title", locale, playerName, Tags.toPathSegment(player.tag()));
    }

    /**
     * @param streak        無ければ null
     * @param averageElixir 使用中のデッキの平均エリクサー。求められなければ null
     * @param stats         対戦履歴の集計。取得できなければ null
     */
    public String playerSummary(PlayerResponse player, String playerName, WinLoseStreak streak, Double averageElixir,
            PlayerBattleStats stats, Locale locale) {
        List<String> sentences = new ArrayList<>();
        sentences.add(labels.message("player.summary.base", locale, playerName, Tags.toPathSegment(player.tag()),
                player.trophies(), player.wins()));
        PlayerResponse.RankedSeasonResult ranked = player.currentPathOfLegendSeasonResult();
        if (ranked != null && ranked.rank() != null) {
            sentences.add(labels.message("player.summary.ranked", locale, ranked.rank()));
        }
        if (streak != null) {
            sentences.add(labels.message("player.summary.streak." + streak.code(), locale, streak.count()));
        }
        if (averageElixir != null) {
            sentences.add(labels.message("player.summary.deck", locale, averageElixir));
        }
        if (stats != null && stats.total() > 0) {
            sentences.add(labels.message("player.summary.recent", locale, stats.wins(), stats.losses()));
        }
        return join(sentences, locale);
    }

    public String clanTitle(ClanResponse clan, String clanName, Locale locale) {
        return labels.message("clan.seo.title", locale, clanName, Tags.toPathSegment(clan.tag()));
    }

    /** @param war クラン対戦の参加状況。不参加・取得失敗なら null */
    public String clanSummary(ClanResponse clan, String clanName, ClanWarParticipation war, Locale locale) {
        List<ClanResponse.Member> members = clan.memberList() == null ? List.of() : clan.memberList();
        List<String> sentences = new ArrayList<>();
        sentences.add(labels.message("clan.summary.base", locale, clanName, Tags.toPathSegment(clan.tag()),
                clan.clanScore(), clan.members()));
        if (!members.isEmpty()) {
            Instant now = clock.instant();
            long active = members.stream()
                    .map(member -> MemberActivity.inactiveDays(member.lastSeen(), now))
                    .filter(OptionalLong::isPresent)
                    .filter(days -> !MemberActivity.isLongInactive(days))
                    .count();
            sentences.add(labels.message("clan.summary.active", locale, active, members.size()));
        }
        if (war != null && war.battleDay()) {
            sentences.add(labels.message("clan.summary.war", locale, war.membersBattledToday(), war.members().size()));
        }
        return join(sentences, locale);
    }

    // 日本語は文の区切りに空白を入れない。
    private static String join(List<String> sentences, Locale locale) {
        return String.join("ja".equals(locale.getLanguage()) ? "" : " ", sentences);
    }
}
