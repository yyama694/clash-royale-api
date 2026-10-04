package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.domain.ClanWarParticipation;
import com.example.clashroyaleapi.domain.MemberActivity;
import com.example.clashroyaleapi.domain.PlayerBattleStats;
import com.example.clashroyaleapi.domain.WinLoseStreak;
import com.example.clashroyaleapi.service.TopPlayerDeckService;
import com.example.clashroyaleapi.web.view.CardUsageView;
import com.example.clashroyaleapi.web.view.ClanJoinView;

import com.ibm.icu.text.ListFormatter;
import com.ibm.icu.util.ULocale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * プレイヤー情報・クラン情報・カード詳細・トッププレイヤーのデッキの各画面の、ページごとのタイトルと要約文。
 * 要約文は検索結果の説明文(meta description・OGP)を兼ね、プレイヤー・クラン画面では本文の先頭の1段落にもなる。
 * 全ページ共通の説明文だと、検索エンジンからは同じ中身のページに見えやすいため(TODO.mdの「ページビュー向上」)。
 */
@Component
public class PageSummaries {

    // 説明文は検索結果で120〜160字ほどしか出ないので、一緒に使われるカードは上位だけにする。
    private static final int SUMMARY_PARTNER_LIMIT = 3;

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

    /**
     * 参加条件(所在地・タイプ・必要トロフィー数)は、クラン名で検索した人やXで共有されたカードを見た人が
     * 入れるかどうかを先に知りたい情報なので、基本の文のすぐ後に置く。
     *
     * @param war クラン対戦の参加状況。不参加・取得失敗なら null
     */
    public String clanSummary(ClanResponse clan, String clanName, ClanJoinView join, ClanWarParticipation war,
            Locale locale) {
        List<ClanResponse.Member> members = clan.memberList() == null ? List.of() : clan.memberList();
        List<String> sentences = new ArrayList<>();
        sentences.add(labels.message("clan.summary.base", locale, clanName, Tags.toPathSegment(clan.tag()),
                clan.clanScore(), clan.members()));
        if (join.typeLabel() != null && join.locationName() != null) {
            sentences.add(labels.message("clan.summary.join", locale, join.locationName(), join.typeLabel(),
                    join.requiredTrophies()));
        }
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

    /** 空きがあって入れるクランは、リーダーがクラン募集にそのまま貼れるよう、募集の文面にする。 */
    public String clanShareText(String clanName, ClanJoinView join, Locale locale) {
        if (join.recruiting() && join.typeLabel() != null) {
            return labels.message("share.clan.recruiting", locale, clanName, join.typeLabel(), join.openSlots(),
                    join.requiredTrophies());
        }
        return labels.message("share.clan.text", locale, clanName);
    }

    public String cardTitle(String cardName, Locale locale) {
        return labels.message("card.seo.title", locale, cardName);
    }

    public String cardSummary(String cardName, CardUsageView usage, Locale locale) {
        List<String> sentences = new ArrayList<>();
        if (usage.users() > 0) {
            sentences.add(labels.message("card.summary.usage", locale, cardName, usage.sampleSize(), usage.users(),
                    usage.percent(), usage.rank(), usage.rankedOf()));
        } else {
            sentences.add(labels.message("card.summary.unused", locale, cardName, usage.sampleSize()));
        }
        if (!usage.partners().isEmpty()) {
            List<String> names = usage.partners().stream()
                    .limit(SUMMARY_PARTNER_LIMIT)
                    .map(CardUsageView.PartnerView::name)
                    .toList();
            sentences.add(labels.message("card.summary.partners", locale,
                    ListFormatter.getInstance(ULocale.forLocale(locale)).format(names)));
        }
        sentences.add(labels.message("card.summary.daily", locale));
        return join(sentences, locale);
    }

    /** @param cardName 絞り込んでいなければ null */
    public String decksHeading(String cardName, Locale locale) {
        return cardName == null ? labels.message("decks.pageTitle", locale)
                : labels.message("decks.heading.card", locale, cardName);
    }

    /**
     * 使っている人数はタイトルにも入れる。カードごとに違う数字になり、検索結果で中身の量も伝わるため。
     *
     * @param cardName 絞り込んでいなければ null
     * @param page     まだ集計していなければ null
     */
    public String decksTitle(String cardName, TopPlayerDeckService.Page page, Locale locale) {
        if (cardName == null || page == null || page.total() == 0) {
            return decksHeading(cardName, locale);
        }
        return labels.message("decks.seo.title.card", locale, cardName, page.total());
    }

    /**
     * @param cardName 絞り込んでいなければ null
     * @param page     まだ集計していなければ null
     * @return 載せる中身が無ければ null(サイト共通の説明文になる)
     */
    public String decksSummary(String cardName, TopPlayerDeckService.Page page, Locale locale) {
        if (page == null) {
            return null;
        }
        if (cardName == null) {
            return labels.message("decks.summary.all", locale, page.sampleSize());
        }
        if (page.total() == 0) {
            return null;
        }
        return labels.message("decks.summary.card", locale, page.sampleSize(), cardName, page.total(),
                page.topRank());
    }

    // 日本語は文の区切りに空白を入れない。
    private static String join(List<String> sentences, Locale locale) {
        return String.join("ja".equals(locale.getLanguage()) ? "" : " ", sentences);
    }
}
