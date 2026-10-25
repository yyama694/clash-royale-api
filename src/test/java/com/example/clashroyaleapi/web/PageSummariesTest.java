package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.dto.ClanRankingResponse;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.config.IcuMessageSource;
import com.example.clashroyaleapi.domain.ClanWarParticipation;
import com.example.clashroyaleapi.domain.LeaderHistory;
import com.example.clashroyaleapi.domain.PlayerBattleStats;
import com.example.clashroyaleapi.domain.RankingMovements;
import com.example.clashroyaleapi.domain.RankingSnapshot;
import com.example.clashroyaleapi.domain.WinLoseStreak;
import com.example.clashroyaleapi.service.PlayerRanking;
import com.example.clashroyaleapi.service.RankingHistoryService;
import com.example.clashroyaleapi.service.TopPlayerDeckService;
import com.example.clashroyaleapi.web.view.BattleDetailView;
import com.example.clashroyaleapi.web.view.CardCatalogGroupView;
import com.example.clashroyaleapi.web.view.CardCatalogItemView;
import com.example.clashroyaleapi.web.view.CardUsageView;
import com.example.clashroyaleapi.web.view.ClanJoinView;
import com.example.clashroyaleapi.web.view.ParticipantView;
import com.example.clashroyaleapi.web.view.ProBadgeView;
import com.example.clashroyaleapi.web.view.ProPlayerView;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 実際の messages*.properties を読んで、組み立てた文を確かめる。 */
class PageSummariesTest {

    // 名前の前後の不可視文字(FSI/PDI)。右から左に書く言語の名前でも前後の並びを崩さないためのもの。
    private static final String FSI = "⁨";
    private static final String PDI = "⁩";

    private final PageSummaries summaries = new PageSummaries(
            new LabelResolver(IcuMessageSource.forBasename("messages")),
            Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC));

    private static PlayerResponse player(PlayerResponse.RankedSeasonResult ranked) {
        return new PlayerResponse("#ABC123", "Taro", 15, 9000, 9200, 3210, 1000, 500, null, List.of(), List.of(),
                ranked, null, List.of());
    }

    private static PlayerBattleStats stats(int wins, int losses) {
        return new PlayerBattleStats(wins + losses, wins, losses, 0, 0, 0, false, List.of(), List.of());
    }

    @Test
    void プレイヤーのタイトルにタグと検索で打たれる語を入れる() {
        assertEquals(FSI + "Taro" + PDI + "(#ABC123)のクラロワ戦績・デッキ",
                summaries.playerTitle(player(null), "Taro", Locale.JAPANESE));
    }

    @Test
    void プレイヤーの要約はある情報だけを文にしてつなぐ() {
        String summary = summaries.playerSummary(player(new PlayerResponse.RankedSeasonResult(7, 2800, 12)), "Taro", null,
                new WinLoseStreak(true, 5, false), 3.125, stats(18, 7), Locale.JAPANESE);

        assertEquals(FSI + "Taro" + PDI + "(#ABC123)のクラロワのプレイヤー情報です。トロフィー9,000、通算3,210勝。"
                + "ランク戦の今シーズンは世界12位。現在5連勝中。使用中のデッキは平均エリクサー3.1。直近の対戦は18勝7敗。", summary);
    }

    @Test
    void プロ_クリエイターの一覧に載せている人は肩書きの文を足す() {
        ProBadgeView pro = new ProBadgeView("Mohamed Light", "pro", "プロ", "SKCalalas", "エジプト");

        assertEquals(FSI + "Taro" + PDI + "(#ABC123)のクラロワのプレイヤー情報です。トロフィー9,000、通算3,210勝。"
                + FSI + "Mohamed Light" + PDI + "はエジプトのプロ選手です。",
                summaries.playerSummary(player(null), "Taro", pro, null, null, null, Locale.JAPANESE));
    }

    @Test
    void プロ_クリエイターの一覧の要約に先頭の3人の名前を入れる() {
        List<ProPlayerView> pros = List.of(proRow("A"), proRow("B"), proRow("C"), proRow("D"));

        assertEquals("クラロワのプロ選手とクリエイター4人が今使っているデッキと、ランク戦の世界順位を紹介。"
                + FSI + "A、B、C" + PDI + "など。デッキはそのままゲームにコピーできます。",
                summaries.prosSummary(pros, Locale.JAPANESE));
        assertTrue(summaries.prosSummary(pros, Locale.ENGLISH)
                .contains("4 Clash Royale pro players and content creators are using right now"));
    }

    private static ProPlayerView proRow(String name) {
        return new ProPlayerView(new ProBadgeView(name, "pro", "Pro", null, "Japan"), name, true, null, null, null,
                14000, null, null, null);
    }

    @Test
    void 順位や連勝や戦績が無ければその文を省く() {
        assertEquals(FSI + "Taro" + PDI + "(#ABC123)のクラロワのプレイヤー情報です。トロフィー9,000、通算3,210勝。",
                summaries.playerSummary(player(new PlayerResponse.RankedSeasonResult(null, 0, null)), "Taro", null, null,
                        null, null, Locale.JAPANESE));
    }

    @Test
    void シーズンの切り替え直後にレーティング0で前のシーズンの順位が残っていても順位の文を省く() {
        assertEquals(FSI + "Taro" + PDI + "(#ABC123)のクラロワのプレイヤー情報です。トロフィー9,000、通算3,210勝。",
                summaries.playerSummary(player(new PlayerResponse.RankedSeasonResult(1, 0, 1)), "Taro", null, null,
                        null, null, Locale.JAPANESE));
    }

    @Test
    void 英語は文を空白で区切り単複を合わせる() {
        String summary = summaries.playerSummary(player(null), "Taro", null, new WinLoseStreak(false, 1, false), null,
                stats(1, 2),
                Locale.ENGLISH);

        assertEquals("Clash Royale player " + FSI + "Taro" + PDI + " (#ABC123) has 9,000 trophies and 3,210 wins in total."
                + " Currently on 1 loss in a row. Recent battles: 1 win and 2 losses.", summary);
    }

    @Test
    void ロシア語の複数形() {
        String summary = summaries.playerSummary(player(null), "Taro", null, new WinLoseStreak(true, 3, false), null, null,
                Locale.forLanguageTag("ru"));

        assertEquals("Игрок Clash Royale " + FSI + "Taro" + PDI + " (#ABC123): 9 000 трофеев и 3 210 побед всего."
                + " Сейчас 3 победы подряд.", summary.replace(' ', ' '));
    }

    @Test
    void 対戦履歴がすべて同じ結果なら少なくとも何連勝かを書く() {
        WinLoseStreak allWins = new WinLoseStreak(true, 30, true);

        assertEquals(FSI + "Taro" + PDI + "(#ABC123)のクラロワのプレイヤー情報です。トロフィー9,000、通算3,210勝。"
                + "現在、少なくとも30連勝中。", summaries.playerSummary(player(null), "Taro", null, allWins, null, null,
                Locale.JAPANESE));
        assertEquals("Clash Royale player " + FSI + "Taro" + PDI + " (#ABC123) has 9,000 trophies and 3,210 wins in total."
                + " Currently on at least 30 wins in a row.", summaries.playerSummary(player(null), "Taro", null, allWins,
                null, null, Locale.ENGLISH));
    }

    @Test
    void クランの要約に参加条件と活動中のメンバー数とクラン対戦の状況を入れる() {
        List<ClanResponse.Member> members = List.of(
                new ClanResponse.Member("#A", "a", "leader", 9000, 100, "20260927T010000.000Z", 0),
                new ClanResponse.Member("#B", "b", "member", 9000, 0, "20260926T010000.000Z", 0),
                new ClanResponse.Member("#C", "c", "member", 9000, 0, "20260901T010000.000Z", 0));
        ClanResponse clan = new ClanResponse("#CLAN1", "Clan", "", 133292, 0, 3, members, "open", 13000, 900, null);
        ClanJoinView join = new ClanJoinView("参加自由", 13000, "日本", 900, 47);
        ClanWarParticipation war = new ClanWarParticipation(true, List.of(
                new ClanWarParticipation.Member("#A", "a", 4, 12),
                new ClanWarParticipation.Member("#B", "b", 0, 8),
                new ClanWarParticipation.Member("#C", "c", 0, 0)));

        assertEquals(FSI + "Clan" + PDI + "(#CLAN1)はクラロワのクランです。クランスコア133,292、メンバー3/50人。"
                        + "所在地は日本、タイプは参加自由、必要トロフィー数は13,000。"
                        + "直近7日以内にアクセスしたメンバーは3人中2人。今日のクラン対戦は3人中1人が攻撃済み。",
                summaries.clanSummary(clan, "Clan", join, war, Locale.JAPANESE));
    }

    @Test
    void クラン対戦の攻撃できない日と不参加なら対戦の文を省き_タイプか所在地が分からなければ参加条件の文を省く() {
        ClanResponse clan = new ClanResponse("#CLAN1", "Clan", "", 100, 0, 0, List.of(), null, 0, 0, null);
        ClanJoinView unknown = new ClanJoinView(null, 0, "日本", 0, 0);
        String expected = FSI + "Clan" + PDI + "(#CLAN1)はクラロワのクランです。クランスコア100、メンバー0/50人。";

        assertEquals(expected, summaries.clanSummary(clan, "Clan", unknown, null, Locale.JAPANESE));
        assertEquals(expected, summaries.clanSummary(clan, "Clan", unknown,
                new ClanWarParticipation(false, List.of()), Locale.JAPANESE));
    }

    @Test
    void 空きがあるクランの共有文は募集の文面にし_満員や参加不可なら通常の文面にする() {
        assertEquals("クラン「Clan」メンバー募集中(参加自由・あと3人・必要トロフィー数13,000)",
                summaries.clanShareText("Clan", new ClanJoinView("参加自由", 13000, "日本", 0, 3), Locale.JAPANESE));
        assertEquals("Clan Clan is recruiting on Clash Royale (Invite Only, 1 spot left, 6,000 trophies required)",
                summaries.clanShareText("Clan", new ClanJoinView("Invite Only", 6000, null, 0, 1), Locale.ENGLISH));
        assertEquals("クラン「Clan」のメンバー・活動状況・クラン対戦の参加状況",
                summaries.clanShareText("Clan", new ClanJoinView("参加自由", 13000, "日本", 0, 0), Locale.JAPANESE));
    }

    private static CardUsageView usage(int users, Integer rank, List<String> partnerNames) {
        List<CardUsageView.PartnerView> partners = partnerNames.stream()
                .map(name -> new CardUsageView.PartnerView(0, name, null, 50))
                .toList();
        return new CardUsageView(Math.round(users * 1000.0 / 988) / 10.0, users, 988, rank, 122, null, null, partners);
    }

    private static TopPlayerDeckService.Page decksPage(int total, Integer topRank) {
        return new TopPlayerDeckService.Page(Instant.parse("2026-10-02T13:20:00Z"), List.of(), total, 1,
                total == 0 ? 0 : 1, false, 988, topRank, null);
    }

    @Test
    void カード詳細のタイトルに検索で打たれる語を入れる() {
        assertEquals(FSI + "ホグライダー" + PDI + "の使用率と一緒に使われるカード【クラロワ】",
                summaries.cardTitle("ホグライダー", Locale.JAPANESE));
        assertEquals(FSI + "Hog Rider" + PDI + ": Clash Royale usage rate and common pairings",
                summaries.cardTitle("Hog Rider", Locale.ENGLISH));
    }

    @Test
    void カード詳細の要約に使用率と一緒に使われるカードの上位3枚を入れる() {
        CardUsageView usage = usage(88, 29, List.of("スケルトン", "ローリングウッド", "アイススピリット", "ロケット砲士", "マイティディガー"));

        assertEquals("クラロワの" + FSI + "ホグライダー" + PDI + "は、ランク戦の世界ランキング上位988人のうち88人(8.9%)の"
                        + "デッキに入っていて、使われていた122枚中29位。一緒に使われることが多いのはスケルトン、ローリングウッド、"
                        + "アイススピリット。毎日更新。",
                summaries.cardSummary("ホグライダー", usage, Locale.JAPANESE));
    }

    @Test
    void 誰も使っていないカードは使っていないことを書き一緒に使われるカードを省く() {
        assertEquals("In Clash Royale, " + FSI + "Mirror" + PDI + " is not in the decks of any of the 988 top players"
                        + " in the global Rank Battle ranking. Updated daily.",
                summaries.cardSummary("Mirror", usage(0, null, List.of()), Locale.ENGLISH));
    }

    @Test
    void 英語のカード詳細の要約は単複を合わせ一緒に使われるカードを英語の並べ方でつなぐ() {
        assertEquals("In Clash Royale, " + FSI + "Hog Rider" + PDI + " is in the decks of 1 player out of 988 top players"
                        + " in the global Rank Battle ranking (0.1%), #100 of 122 cards used."
                        + " Often paired with Skeletons and Ice Spirit. Updated daily.",
                summaries.cardSummary("Hog Rider", usage(1, 100, List.of("Skeletons", "Ice Spirit")), Locale.ENGLISH));
    }

    @Test
    void 共有文にはトロフィーと世界の順位と連勝を入れる() {
        PlayerResponse ranked = player(new PlayerResponse.RankedSeasonResult(7, 2800, 12));

        assertEquals("Taro のクラロワ戦績(トロフィー9,000、世界12位、5連勝中)",
                summaries.playerShareText(ranked, "Taro", new WinLoseStreak(true, 5, false), Locale.JAPANESE));
        assertEquals("Taro's Clash Royale stats: 9,000 trophies, #12 worldwide, and 5 wins in a row",
                summaries.playerShareText(ranked, "Taro", new WinLoseStreak(true, 5, false), Locale.ENGLISH));
    }

    @Test
    void 共有文に連敗と順位の無いランク戦は入れない() {
        assertEquals("Taro のクラロワ戦績(トロフィー9,000)",
                summaries.playerShareText(player(new PlayerResponse.RankedSeasonResult(1, 0, 1)), "Taro",
                        new WinLoseStreak(false, 3, false), Locale.JAPANESE));
    }

    @Test
    void 対戦詳細のタイトルには両者の名前を入れ2v2は2人ずつつなぐ() {
        assertEquals(FSI + "Taro" + PDI + " vs " + FSI + "Jiro" + PDI + " の対戦詳細",
                summaries.battleTitle(battle(List.of("Taro"), List.of("Jiro")), Locale.JAPANESE));
        assertEquals(FSI + "Taro & Hanako" + PDI + " vs " + FSI + "Jiro & Saburo" + PDI + ": battle detail",
                summaries.battleTitle(battle(List.of("Taro", "Hanako"), List.of("Jiro", "Saburo")), Locale.ENGLISH));
    }

    private static BattleDetailView battle(List<String> team, List<String> opponents) {
        return new BattleDetailView("20261011T000000.000Z", null, "Ranked", participants(team), participants(opponents));
    }

    private static List<ParticipantView> participants(List<String> names) {
        return names.stream()
                .map(name -> new ParticipantView("#" + name, name, name, 0, false, null, List.of(), List.of(), null, false,
                        null, null, List.of()))
                .toList();
    }

    @Test
    void 使用率ランキングの要約には使われていた上位3枚を順に入れる() {
        List<CardCatalogGroupView> groups = List.of(
                new CardCatalogGroupView("カード", List.of(ranked("ザップ", 1), ranked("ホグライダー", 2),
                        ranked("矢の雨", 2), ranked("スケルトン", 4))),
                new CardCatalogGroupView("タワーユニット", List.of(ranked("タワープリンセス", 1))));

        assertEquals("クラロワのランク戦の世界ランキング上位988人のデッキで、よく使われているカードの順位。"
                        + "上位はザップ、ホグライダー、矢の雨。毎日更新。",
                summaries.cardUsageRankingSummary(988, groups, Locale.JAPANESE));
        assertEquals("Clash Royale cards ranked by how many of the 988 top players in the global Rank Battle ranking"
                        + " use them. Most used: Zap, Hog Rider, and Arrows. Updated daily.",
                summaries.cardUsageRankingSummary(988, List.of(new CardCatalogGroupView("Cards",
                        List.of(ranked("Zap", 1), ranked("Hog Rider", 2), ranked("Arrows", 3)))), Locale.ENGLISH));
    }

    @Test
    void 使われていたカードが無ければ使用率ランキングの要約はサイト共通にする() {
        assertNull(summaries.cardUsageRankingSummary(0,
                List.of(new CardCatalogGroupView("カード", List.of(ranked("鏡", null)))), Locale.JAPANESE));
    }

    private static CardCatalogItemView ranked(String name, Integer rank) {
        return new CardCatalogItemView(1, name, name, null, null, null, rank == null ? 0.0 : 10.0, rank, null);
    }

    @Test
    void カードで絞り込んだデッキ画面はカード名と使っている人数をタイトルに入れる() {
        TopPlayerDeckService.Page page = decksPage(88, 6);

        assertEquals(FSI + "ホグライダー" + PDI + "入りのデッキ",
                summaries.decksHeading(List.of("ホグライダー"), Locale.JAPANESE));
        assertEquals(FSI + "ホグライダー" + PDI + "入りのデッキ(世界トップ層の88人が使用中)【クラロワ】",
                summaries.decksTitle(List.of("ホグライダー"), page, Locale.JAPANESE));
        assertEquals("クラロワのランク戦の世界ランキング上位988人のうち、" + FSI + "ホグライダー" + PDI
                        + "を入れている88人のデッキを順位の順に紹介。最上位は世界6位。平均エリクサーも分かり、"
                        + "そのままゲームにコピーできます。毎日更新。",
                summaries.decksSummary(List.of("ホグライダー"), page, Locale.JAPANESE));
    }

    @Test
    void 絞り込んだカードを誰も使っていないか集計前なら人数を書かず説明文はサイト共通にする() {
        String heading = FSI + "Mirror" + PDI + " decks";

        assertEquals(heading, summaries.decksTitle(List.of("Mirror"), decksPage(0, null), Locale.ENGLISH));
        assertNull(summaries.decksSummary(List.of("Mirror"), decksPage(0, null), Locale.ENGLISH));
        assertEquals(heading, summaries.decksTitle(List.of("Mirror"), null, Locale.ENGLISH));
        assertNull(summaries.decksSummary(List.of("Mirror"), null, Locale.ENGLISH));
    }

    @Test
    void 複数のカードで絞り込んだデッキ画面はカード名をその言語の並べ方でつなぐ() {
        TopPlayerDeckService.Page page = decksPage(12, 3);

        assertEquals(FSI + "Hog Rider, The Log, and Earthquake" + PDI + " decks",
                summaries.decksHeading(List.of("Hog Rider", "The Log", "Earthquake"), Locale.ENGLISH));
        assertEquals(FSI + "ホグライダー、アースクエイク" + PDI + "入りのデッキ(世界トップ層の12人が使用中)【クラロワ】",
                summaries.decksTitle(List.of("ホグライダー", "アースクエイク"), page, Locale.JAPANESE));
    }

    @Test
    void 人気のデッキ画面は集計した人数とデッキの種類の数を説明文に入れ_絞り込んだ画面は見出しだけにする() {
        TopPlayerDeckService.PopularPage page = new TopPlayerDeckService.PopularPage(
                Instant.parse("2026-10-02T13:20:00Z"), List.of(), 97, 1, 1, true, 988, 988, null);

        assertEquals("人気のデッキ", summaries.popularDecksHeading(List.of(), Locale.JAPANESE));
        assertEquals("人気のデッキ(世界トップ層で同じ8枚を使う人数の順)【クラロワ】",
                summaries.popularDecksTitle(List.of(), Locale.JAPANESE));
        assertEquals("クラロワのランク戦の世界ランキング上位988人のデッキのうち、8枚とも同じデッキを2人以上が使っている"
                        + "97種類を、使っている人数の多い順に紹介。そのままゲームにコピーできます。毎日更新。",
                summaries.popularDecksSummary(List.of(), page, Locale.JAPANESE));

        assertEquals(FSI + "ホグライダー" + PDI + "入りの人気のデッキ",
                summaries.popularDecksTitle(List.of("ホグライダー"), Locale.JAPANESE));
        assertNull(summaries.popularDecksSummary(List.of("ホグライダー"), page, Locale.JAPANESE));
        assertNull(summaries.popularDecksSummary(List.of(), null, Locale.JAPANESE));
    }

    @Test
    void 絞り込まないデッキ画面は画面名をタイトルにし集計した人数を説明文に入れる() {
        TopPlayerDeckService.Page page = decksPage(988, 1);

        assertEquals("トッププレイヤーのデッキ", summaries.decksHeading(List.of(), Locale.JAPANESE));
        assertEquals("トッププレイヤーのデッキ", summaries.decksTitle(List.of(), page, Locale.JAPANESE));
        assertEquals("クラロワのランク戦の世界ランキング上位988人が、直近のランク戦で使ったデッキを順位の順に紹介。"
                        + "平均エリクサーも分かり、カードで絞り込んで、そのままゲームにコピーできます。毎日更新。",
                summaries.decksSummary(List.of(), page, Locale.JAPANESE));
    }

    private static RankingSnapshot.Entry entry(int rank, String name, int rating) {
        return new RankingSnapshot.Entry(rank, "#" + name, name, rating);
    }

    private static RankingHistoryService.Report movementsReport(LocalDate day, String beforeAt,
            List<LeaderHistory.Change> changes, LeaderHistory.Reign reign) {
        RankingSnapshot before = new RankingSnapshot(Instant.parse(beforeAt), "2026-09",
                List.of(entry(1, "Miku", 3000), entry(2, "Taro", 2990), entry(3, "Hana", 2900)));
        RankingSnapshot after = new RankingSnapshot(Instant.parse("2026-10-06T23:46:00Z"), "2026-09",
                List.of(entry(1, "Taro", 3020), entry(2, "Miku", 3008), entry(3, "Hana", 2980)));
        return new RankingHistoryService.Report(day, RankingMovements.between(before, after, 10, 10), changes, reign);
    }

    @Test
    void 最新の動きの要約は1位と2位の差と1位を守っている時間とレーティングを一番上げた人を書く() {
        RankingHistoryService.Report report = movementsReport(null, "2026-10-05T23:46:00Z", List.of(),
                new LeaderHistory.Reign(entry(1, "Taro", 3020), Instant.parse("2026-10-06T18:01:00Z"), false));

        assertEquals("今の世界1位は" + FSI + "Taro" + PDI + "(レーティング3,020)。2位の" + FSI + "Miku" + PDI
                        + "とは12ポイント差です。" + FSI + "Taro" + PDI + "は5時間、1位を守っています。"
                        + "直近24時間でレーティングを一番上げたのは" + FSI + "Hana" + PDI + "で、+80(3位→3位)。"
                        + "今シーズンのランキングに載っているのは、今のところ3人です。",
                summaries.movementsSummary(report, Locale.JAPANESE));
        assertEquals("ランク戦の今の世界1位は" + FSI + "Taro" + PDI + " 首位交代と急上昇まとめ【クラロワ】",
                summaries.movementsTitle(report, null, Locale.JAPANESE));
    }

    @Test
    void シーズンの最初の記録から1位のままなら最初の記録からと断る() {
        RankingHistoryService.Report report = movementsReport(null, "2026-10-04T23:46:00Z", List.of(),
                new LeaderHistory.Reign(entry(1, "Taro", 3020), Instant.parse("2026-10-04T09:00:00Z"), true));

        assertTrue(summaries.movementsSummary(report, Locale.ENGLISH).contains(
                FSI + "Taro" + PDI + " has been #1 ever since this season's first record, 2 days ago."));
    }

    @Test
    void 記録を始めて1時間たたない1位は1位の期間を書かない() {
        RankingHistoryService.Report report = movementsReport(null, "2026-10-06T23:31:00Z", List.of(),
                new LeaderHistory.Reign(entry(1, "Taro", 3020), Instant.parse("2026-10-06T23:31:00Z"), true));

        String summary = summaries.movementsSummary(report, Locale.ENGLISH);

        assertFalse(summary.contains("held"), summary);
        assertTrue(summary.contains("in the last hour"), summary);
    }

    @Test
    void 日ごとの動きの要約は首位交代の回数を書く() {
        LocalDate day = LocalDate.parse("2026-10-06");
        RankingHistoryService.Report report = movementsReport(day, "2026-10-05T23:46:00Z",
                List.of(new LeaderHistory.Change(Instant.parse("2026-10-06T18:01:00Z"), entry(1, "Taro", 3010),
                        entry(1, "Miku", 3005))), null);

        assertEquals(FSI + "Taro" + PDI + " ended the day as world #1 with a rating of 3,020, 12 points ahead of "
                        + FSI + "Miku" + PDI + " in 2nd. World #1 changed hands once during the day. "
                        + "The biggest rating gain of the day belongs to " + FSI + "Hana" + PDI
                        + ": +80, from #3 to #3.",
                summaries.movementsSummary(report, Locale.ENGLISH));
        assertEquals("Clash Royale Rank Battle ranking on October 6, 2026: world #1 " + FSI + "Taro" + PDI,
                summaries.movementsTitle(report, day, Locale.ENGLISH));
        assertEquals("2026年10月6日のランク戦 世界ランキングの動き", summaries.movementsHeading(day, Locale.JAPANESE));
    }

    @Test
    void 前の日から1位が替わらなければ1日中守ったと書きその日の途中からの記録なら書かない() {
        LocalDate day = LocalDate.parse("2026-10-06");

        assertTrue(summaries.movementsSummary(movementsReport(day, "2026-10-05T23:46:00Z", List.of(), null),
                Locale.ENGLISH).contains(FSI + "Taro" + PDI + " held #1 all day."));
        assertFalse(summaries.movementsSummary(movementsReport(day, "2026-10-06T09:01:00Z", List.of(), null),
                Locale.ENGLISH).contains("all day"));
    }

    @Test
    void フランス語の順位は1位だけ序数の語尾を変える() {
        RankingSnapshot before = new RankingSnapshot(Instant.parse("2026-10-05T23:46:00Z"), "2026-09",
                List.of(entry(1, "Miku", 3000), entry(2, "Taro", 2900)));
        RankingSnapshot after = new RankingSnapshot(Instant.parse("2026-10-06T23:46:00Z"), "2026-09",
                List.of(entry(1, "Taro", 3050), entry(2, "Miku", 3000)));
        RankingHistoryService.Report report = new RankingHistoryService.Report(LocalDate.parse("2026-10-06"),
                RankingMovements.between(before, after, 10, 10), List.of(), null);

        assertTrue(summaries.movementsSummary(report, Locale.FRENCH).endsWith("de la 2e à la 1re place."));
    }

    @Test
    void まだ記録が無ければ画面名をタイトルにし説明文はサイト共通にする() {
        assertEquals("ランク戦 世界ランキングの動き", summaries.movementsTitle(null, null, Locale.JAPANESE));
        assertNull(summaries.movementsSummary(null, Locale.JAPANESE));
    }

    @Test
    void ロシア語のデッキ画面の要約は人数に合わせて動詞と名詞を変える() {
        String summary = summaries.decksSummary(List.of("Всадник на кабане"), decksPage(2, 6), Locale.forLanguageTag("ru"))
                .replace(' ', ' ');

        assertTrue(summary.startsWith("Из 988 топ-игроков мирового рейтинга Clash Royale в рейтинговом режиме карту "
                + FSI + "Всадник на кабане" + PDI + " используют 2 игрока."), summary);
    }
    @Test
    void 国別ランキングのタイトルと説明文に国名と上位3人を入れる() {
        List<PlayerRankingResponse.RankedPlayer> players = List.of(
                new PlayerRankingResponse.RankedPlayer("#A", "Taro", 2900, 1, null),
                new PlayerRankingResponse.RankedPlayer("#B", "Jiro", 2850, 2, null),
                new PlayerRankingResponse.RankedPlayer("#C", "Saburo", 2800, 3, null));

        assertEquals("日本の個人ランキング", summaries.countryPlayerRankingTitle("日本", Locale.JAPANESE));
        assertEquals("クラロワのランク戦で、日本のプレイヤーの今シーズンの順位。1位はTaro"
                        + "(レーティング2,900)、2位はJiro(2,850)、3位はSaburo(2,800)。",
                summaries.countryPlayerRankingSummary("日本", PlayerRanking.current(players), Locale.JAPANESE));
        // 前のシーズンの順位を代わりに出しているとき・3人そろわないときは、今シーズンの上位と書かない。
        assertEquals("クラロワのランク戦で、日本のプレイヤーの今シーズンの順位。",
                summaries.countryPlayerRankingSummary("日本",
                        PlayerRanking.finishedSeason(players, java.time.YearMonth.of(2026, 9)), Locale.JAPANESE));
        assertEquals("クラロワのランク戦で、日本のプレイヤーの今シーズンの順位。",
                summaries.countryPlayerRankingSummary("日本", PlayerRanking.newSeason(players.subList(0, 2)),
                        Locale.JAPANESE));
    }

    @Test
    void 国別のクランランキングの説明文に上位3クランを入れる() {
        List<ClanRankingResponse.RankedClan> clans = List.of(
                new ClanRankingResponse.RankedClan("#A", "Alpha", 1, 100000, 50, null),
                new ClanRankingResponse.RankedClan("#B", "Beta", 2, 99000, 50, null),
                new ClanRankingResponse.RankedClan("#C", "Gamma", 3, 98000, 50, null));

        assertEquals("Brasil clan ranking", summaries.countryClanRankingTitle("Brasil", Locale.ENGLISH));
        assertEquals("Ranking de clãs de Clash Royale: Brasil. 1º Alpha, 2º "
                        + "Beta, 3º Gamma.",
                summaries.countryClanRankingSummary("Brasil", clans, Locale.forLanguageTag("pt")));
        assertEquals("Ranking de clãs de Clash Royale: Brasil.",
                summaries.countryClanRankingSummary("Brasil", clans.subList(0, 1), Locale.forLanguageTag("pt")));
    }
}
