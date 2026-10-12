package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.Tags;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.domain.ClanWarParticipation;
import com.example.clashroyaleapi.domain.GameText;
import com.example.clashroyaleapi.domain.LeaderHistory;
import com.example.clashroyaleapi.domain.MemberActivity;
import com.example.clashroyaleapi.domain.PlayerBattleStats;
import com.example.clashroyaleapi.domain.RankingMovements;
import com.example.clashroyaleapi.domain.RankingSnapshot;
import com.example.clashroyaleapi.domain.WinLoseStreak;
import com.example.clashroyaleapi.service.RankingHistoryService;
import com.example.clashroyaleapi.service.RankingService;
import com.example.clashroyaleapi.service.TopPlayerDeckService;
import com.example.clashroyaleapi.web.view.BattleDetailView;
import com.example.clashroyaleapi.web.view.CardCatalogGroupView;
import com.example.clashroyaleapi.web.view.CardCatalogItemView;
import com.example.clashroyaleapi.web.view.CardUsageView;
import com.example.clashroyaleapi.web.view.ClanJoinView;
import com.example.clashroyaleapi.web.view.ParticipantView;

import com.ibm.icu.text.ListFormatter;
import com.ibm.icu.util.ULocale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Collectors;

/**
 * プレイヤー情報・クラン情報・カード詳細・カードの使用率ランキング・トッププレイヤーのデッキ・ランキングの動きの各画面の、
 * ページごとのタイトルと要約文。
 * 要約文は検索結果の説明文(meta description・OGP)。ランキングの動き画面では本文の先頭の1段落にもなる。
 * プレイヤー・クラン画面の本文には出さない。すぐ下の表と同じことを繰り返すだけになるため(2026-10-11にユーザーが指摘)。
 * 全ページ共通の説明文だと、検索エンジンからは同じ中身のページに見えやすいため(TODO.mdの「ページビュー向上」)。
 */
@Component
public class PageSummaries {

    // 説明文は検索結果で120〜160字ほどしか出ないので、一緒に使われるカードは上位だけにする。
    private static final int SUMMARY_PARTNER_LIMIT = 3;
    private static final int SUMMARY_TOP_CARDS = 3;

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
        if (ranked != null && ranked.hasRank()) {
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

    /**
     * 共有ボタンの文面。画面の上段と同じ数字(トロフィー・ランク戦の世界の順位・連勝)を入れ、Xのタイムラインで目を引くようにする。
     * 連敗は本人が共有したい情報ではないので入れない。
     *
     * @param streak 無ければ null
     */
    public String playerShareText(PlayerResponse player, String playerName, WinLoseStreak streak, Locale locale) {
        List<String> stats = new ArrayList<>();
        stats.add(labels.message("share.player.trophies", locale, player.trophies()));
        PlayerResponse.RankedSeasonResult ranked = player.currentPathOfLegendSeasonResult();
        if (ranked != null && ranked.hasRank()) {
            stats.add(labels.message("player.ranked.rank", locale, ranked.rank()));
        }
        if (streak != null && streak.winning()) {
            stats.add(labels.message("player.streak." + streak.code(), locale, streak.count()));
        }
        return labels.message("share.player.text", locale, playerName,
                ListFormatter.getInstance(ULocale.forLocale(locale)).format(stats));
    }

    /** 対戦詳細のタイトル。全部「対戦詳細」だと、いくつも開いたときにタブで見分けられないため、両者の名前を入れる。 */
    public String battleTitle(BattleDetailView battle, Locale locale) {
        return labels.message("battle.seo.title", locale, names(battle.team()), names(battle.opponents()));
    }

    // 2v2は「A & B」。言語によって「と」「and」と書き分けるほどの所ではないので記号でつなぐ。
    private static String names(List<ParticipantView> side) {
        return side.stream().map(ParticipantView::name).collect(Collectors.joining(" & "));
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

    public String cardUsageRankingTitle(Locale locale) {
        return labels.message("cards.usage.seo.title", locale);
    }

    /**
     * 使用率ランキングの説明文。上位のカード名を入れ、検索結果で中身が分かるようにする。
     *
     * @param groups {@link ViewMapper#toCardUsageRanking} の結果(先頭が通常のカード)
     * @return 使われていたカードが無ければ null(サイト共通の説明文になる)
     */
    public String cardUsageRankingSummary(int sampleSize, List<CardCatalogGroupView> groups, Locale locale) {
        List<String> names = groups.isEmpty() ? List.of() : groups.get(0).cards().stream()
                .filter(card -> card.usageRank() != null)
                .limit(SUMMARY_TOP_CARDS)
                .map(CardCatalogItemView::name)
                .toList();
        if (names.isEmpty()) {
            return null;
        }
        return labels.message("cards.usage.summary", locale, sampleSize,
                ListFormatter.getInstance(ULocale.forLocale(locale)).format(names));
    }

    /** @param cardNames 絞り込んだカードの名前。絞り込んでいなければ空 */
    public String decksHeading(List<String> cardNames, Locale locale) {
        return cardNames.isEmpty() ? labels.message("decks.pageTitle", locale)
                : labels.message("decks.heading.card", locale, cardList(cardNames, locale));
    }

    /**
     * 使っている人数はタイトルにも入れる。カードごとに違う数字になり、検索結果で中身の量も伝わるため。
     *
     * @param cardNames 絞り込んだカードの名前。絞り込んでいなければ空
     * @param page      まだ集計していなければ null
     */
    public String decksTitle(List<String> cardNames, TopPlayerDeckService.Page page, Locale locale) {
        if (cardNames.isEmpty() || page == null || page.total() == 0) {
            return decksHeading(cardNames, locale);
        }
        return labels.message("decks.seo.title.card", locale, cardList(cardNames, locale), page.total());
    }

    /**
     * @param cardNames 絞り込んだカードの名前。絞り込んでいなければ空
     * @param page      まだ集計していなければ null
     * @return 載せる中身が無ければ null(サイト共通の説明文になる)
     */
    public String decksSummary(List<String> cardNames, TopPlayerDeckService.Page page, Locale locale) {
        if (page == null) {
            return null;
        }
        if (cardNames.isEmpty()) {
            return labels.message("decks.summary.all", locale, page.sampleSize());
        }
        if (page.total() == 0) {
            return null;
        }
        return labels.message("decks.summary.card", locale, page.sampleSize(), cardList(cardNames, locale),
                page.total(), page.topRank());
    }

    /** 複数枚はその言語の「AとB」の形でつなぐ(すべて入ったデッキなので、「または」の形にはしない)。 */
    private static String cardList(List<String> cardNames, Locale locale) {
        return ListFormatter.getInstance(ULocale.forLocale(locale)).format(cardNames);
    }

    /** @param day 日ごとの動きならその日(UTC)、最新の動きなら null */
    public String movementsHeading(LocalDate day, Locale locale) {
        return day == null ? labels.message("movements.heading", locale)
                : labels.message("movements.heading.day", locale, dateOf(day));
    }

    /**
     * 1位の名前をタイトルに入れる。「クラロワ 世界1位」のような検索で、その時点の1位の名前ごと拾われるようにするため。
     *
     * @param report まだ記録が無ければ null
     */
    public String movementsTitle(RankingHistoryService.Report report, LocalDate day, Locale locale) {
        if (report == null) {
            return movementsHeading(day, locale);
        }
        String leader = report.movements().after().entries().getFirst().name();
        return day == null ? labels.message("movements.seo.title", locale, leader)
                : labels.message("movements.seo.title.day", locale, leader, dateOf(day));
    }

    /**
     * 本文の先頭の段落を兼ねる。1位と2位との差、いつから1位か(日ごとの動きなら、その日の首位交代の回数)、
     * レーティングを一番上げた人を文にする。
     *
     * @return まだ記録が無ければ null(サイト共通の説明文になる)
     */
    public String movementsSummary(RankingHistoryService.Report report, Locale locale) {
        if (report == null) {
            return null;
        }
        boolean latest = report.day() == null;
        String prefix = latest ? "movements.now." : "movements.day.";
        RankingMovements movements = report.movements();
        RankingSnapshot after = movements.after();
        RankingSnapshot.Entry leader = after.entries().getFirst();
        List<String> sentences = new ArrayList<>();
        if (after.entries().size() > 1) {
            RankingSnapshot.Entry second = after.entries().get(1);
            sentences.add(labels.message(prefix + "leader", locale, leader.name(), leader.rating(),
                    leader.rating() - second.rating(), second.name()));
        } else {
            sentences.add(labels.message(prefix + "leaderOnly", locale, leader.name(), leader.rating()));
        }
        if (latest) {
            reignSentence(report.reign(), after, locale).ifPresent(sentences::add);
        } else if (!report.leaderChanges().isEmpty()) {
            sentences.add(labels.message("movements.day.changes", locale, report.leaderChanges().size()));
        } else if (movements.before() != null
                && movements.before().takenAt().isBefore(report.day().atStartOfDay(ZoneOffset.UTC).toInstant())) {
            // 前の日の記録から1位が同じときだけ「1日中」と言える(その日の途中から記録した日は言えない)。
            sentences.add(labels.message("movements.day.noChange", locale, leader.name()));
        }
        if (!movements.climbers().isEmpty()) {
            RankingMovements.Climber top = movements.climbers().getFirst();
            sentences.add(latest
                    ? labels.message("movements.now.climber", locale,
                            roundedHours(Duration.between(movements.before().takenAt(), after.takenAt())),
                            top.entry().name(), top.ratingGain(), top.previousRank(), top.entry().rank())
                    : labels.message("movements.day.climber", locale, top.entry().name(), top.ratingGain(),
                            top.previousRank(), top.entry().rank()));
        }
        if (latest && after.entries().size() < RankingService.MAX_PLAYER_RANKING_SIZE) {
            sentences.add(labels.message("movements.now.count", locale, after.entries().size()));
        }
        return join(sentences, locale);
    }

    /** 1時間未満は、記録を始めたばかりで1位が替わったのか分からないことがあるので、交代したと言えるときだけ出す。 */
    private Optional<String> reignSentence(LeaderHistory.Reign reign, RankingSnapshot after, Locale locale) {
        if (reign == null || !reign.leader().tag().equals(after.entries().getFirst().tag())) {
            return Optional.empty();
        }
        String name = reign.leader().name();
        Duration held = Duration.between(reign.since(), after.takenAt());
        String kind = reign.sinceFirstRecord() ? "movements.now.reign.firstRecord." : "movements.now.reign.";
        if (held.toHours() < 1) {
            return reign.sinceFirstRecord() ? Optional.empty()
                    : Optional.of(labels.message("movements.now.reign.recent", locale, name));
        }
        if (held.toHours() < 48) {
            return Optional.of(labels.message(kind + "hours", locale, name, held.toHours()));
        }
        return Optional.of(labels.message(kind + "days", locale, name, held.toDays()));
    }

    private static long roundedHours(Duration duration) {
        return Math.max(1, Math.round(duration.toMinutes() / 60.0));
    }

    // 日付は言語ごとにICUに書かせる(引数は日時)。サーバーのタイムゾーンで前後の日にずれないよう、その日の正午(UTC)にする。
    public String seasonHeading(YearMonth season, Locale locale) {
        return labels.message("season.heading", locale, monthOf(season));
    }

    public String seasonLead(YearMonth season, int players, Locale locale) {
        return labels.message("season.lead", locale, monthOf(season), players);
    }

    /** 「クラロワ 〇月 シーズン 最終順位」「〇〇 世界1位」のような検索で拾われるよう、月と1位の名前を入れる。 */
    public String seasonTitle(YearMonth season, List<PlayerRankingResponse.RankedPlayer> players, Locale locale) {
        return labels.message("season.seo.title", locale, monthOf(season),
                GameText.stripFormatting(players.getFirst().name()));
    }

    /** 上位3人の名前とレーティング。3人そろわなければ本文の導入と同じ文にする。 */
    public String seasonSummary(YearMonth season, List<PlayerRankingResponse.RankedPlayer> players, Locale locale) {
        if (players.size() < 3) {
            return seasonLead(season, players.size(), locale);
        }
        List<Object> args = new ArrayList<>(List.of(monthOf(season)));
        players.stream().limit(3).forEach(player -> {
            args.add(GameText.stripFormatting(player.name()));
            args.add(player.eloRating());
        });
        return labels.message("season.summary", locale, args.toArray());
    }

    // 月名は言語ごとにICUに書かせる(引数は日時)。サーバーのタイムゾーンで前後の月にずれないよう、月の半ばにする。
    private static Date monthOf(YearMonth season) {
        return Date.from(season.atDay(15).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    private static Date dateOf(LocalDate day) {
        return Date.from(day.atTime(12, 0).toInstant(ZoneOffset.UTC));
    }

    private static String join(List<String> sentences, Locale locale) {
        return String.join(SupportedLanguages.sentenceSeparator(locale), sentences);
    }
}
