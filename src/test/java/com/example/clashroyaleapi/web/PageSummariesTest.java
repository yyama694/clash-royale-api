package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.config.IcuMessageSource;
import com.example.clashroyaleapi.domain.ClanWarParticipation;
import com.example.clashroyaleapi.domain.PlayerBattleStats;
import com.example.clashroyaleapi.domain.WinLoseStreak;
import com.example.clashroyaleapi.service.TopPlayerDeckService;
import com.example.clashroyaleapi.web.view.CardUsageView;
import com.example.clashroyaleapi.web.view.ClanJoinView;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        String summary = summaries.playerSummary(player(new PlayerResponse.RankedSeasonResult(7, 2800, 12)), "Taro",
                new WinLoseStreak(true, 5, false), 3.125, stats(18, 7), Locale.JAPANESE);

        assertEquals(FSI + "Taro" + PDI + "(#ABC123)のクラロワのプレイヤー情報です。トロフィー9,000、通算3,210勝。"
                + "ランク戦の今シーズンは世界12位。現在5連勝中。使用中のデッキは平均エリクサー3.1。直近の対戦は18勝7敗。", summary);
    }

    @Test
    void 順位や連勝や戦績が無ければその文を省く() {
        assertEquals(FSI + "Taro" + PDI + "(#ABC123)のクラロワのプレイヤー情報です。トロフィー9,000、通算3,210勝。",
                summaries.playerSummary(player(new PlayerResponse.RankedSeasonResult(null, 0, null)), "Taro", null,
                        null, null, Locale.JAPANESE));
    }

    @Test
    void 英語は文を空白で区切り単複を合わせる() {
        String summary = summaries.playerSummary(player(null), "Taro", new WinLoseStreak(false, 1, false), null,
                stats(1, 2),
                Locale.ENGLISH);

        assertEquals("Clash Royale player " + FSI + "Taro" + PDI + " (#ABC123) has 9,000 trophies and 3,210 wins in total."
                + " Currently on 1 loss in a row. Recent battles: 1 win and 2 losses.", summary);
    }

    @Test
    void ロシア語の複数形() {
        String summary = summaries.playerSummary(player(null), "Taro", new WinLoseStreak(true, 3, false), null, null,
                Locale.forLanguageTag("ru"));

        assertEquals("Игрок Clash Royale " + FSI + "Taro" + PDI + " (#ABC123): 9 000 трофеев и 3 210 побед всего."
                + " Сейчас 3 победы подряд.", summary.replace(' ', ' '));
    }

    @Test
    void 対戦履歴がすべて同じ結果なら少なくとも何連勝かを書く() {
        WinLoseStreak allWins = new WinLoseStreak(true, 30, true);

        assertEquals(FSI + "Taro" + PDI + "(#ABC123)のクラロワのプレイヤー情報です。トロフィー9,000、通算3,210勝。"
                + "現在、少なくとも30連勝中。", summaries.playerSummary(player(null), "Taro", allWins, null, null,
                Locale.JAPANESE));
        assertEquals("Clash Royale player " + FSI + "Taro" + PDI + " (#ABC123) has 9,000 trophies and 3,210 wins in total."
                + " Currently on at least 30 wins in a row.", summaries.playerSummary(player(null), "Taro", allWins,
                null, null, Locale.ENGLISH));
    }

    @Test
    void クランの要約に参加条件と活動中のメンバー数とクラン対戦の状況を入れる() {
        List<ClanResponse.Member> members = List.of(
                new ClanResponse.Member("#A", "a", "leader", 9000, 100, "20260927T010000.000Z"),
                new ClanResponse.Member("#B", "b", "member", 9000, 0, "20260926T010000.000Z"),
                new ClanResponse.Member("#C", "c", "member", 9000, 0, "20260901T010000.000Z"));
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
        return new CardUsageView(Math.round(users * 1000.0 / 988) / 10.0, users, 988, rank, 122, null, partners);
    }

    private static TopPlayerDeckService.Page decksPage(int total, Integer topRank) {
        return new TopPlayerDeckService.Page(Instant.parse("2026-10-02T13:20:00Z"), List.of(), total, 1,
                total == 0 ? 0 : 1, false, 988, topRank);
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
    void カードで絞り込んだデッキ画面はカード名と使っている人数をタイトルに入れる() {
        TopPlayerDeckService.Page page = decksPage(88, 6);

        assertEquals(FSI + "ホグライダー" + PDI + "入りのデッキ", summaries.decksHeading("ホグライダー", Locale.JAPANESE));
        assertEquals(FSI + "ホグライダー" + PDI + "入りのデッキ(世界トップ層の88人が使用中)【クラロワ】",
                summaries.decksTitle("ホグライダー", page, Locale.JAPANESE));
        assertEquals("クラロワのランク戦の世界ランキング上位988人のうち、" + FSI + "ホグライダー" + PDI
                        + "を入れている88人のデッキを順位の順に紹介。最上位は世界6位。平均エリクサーも分かり、"
                        + "そのままゲームにコピーできます。毎日更新。",
                summaries.decksSummary("ホグライダー", page, Locale.JAPANESE));
    }

    @Test
    void 絞り込んだカードを誰も使っていないか集計前なら人数を書かず説明文はサイト共通にする() {
        String heading = FSI + "Mirror" + PDI + " decks";

        assertEquals(heading, summaries.decksTitle("Mirror", decksPage(0, null), Locale.ENGLISH));
        assertNull(summaries.decksSummary("Mirror", decksPage(0, null), Locale.ENGLISH));
        assertEquals(heading, summaries.decksTitle("Mirror", null, Locale.ENGLISH));
        assertNull(summaries.decksSummary("Mirror", null, Locale.ENGLISH));
    }

    @Test
    void 絞り込まないデッキ画面は画面名をタイトルにし集計した人数を説明文に入れる() {
        TopPlayerDeckService.Page page = decksPage(988, 1);

        assertEquals("トッププレイヤーのデッキ", summaries.decksHeading(null, Locale.JAPANESE));
        assertEquals("トッププレイヤーのデッキ", summaries.decksTitle(null, page, Locale.JAPANESE));
        assertEquals("クラロワのランク戦の世界ランキング上位988人が、直近のランク戦で使ったデッキを順位の順に紹介。"
                        + "平均エリクサーも分かり、カードで絞り込んで、そのままゲームにコピーできます。毎日更新。",
                summaries.decksSummary(null, page, Locale.JAPANESE));
    }

    @Test
    void ロシア語のデッキ画面の要約は人数に合わせて動詞と名詞を変える() {
        String summary = summaries.decksSummary("Всадник на кабане", decksPage(2, 6), Locale.forLanguageTag("ru"))
                .replace(' ', ' ');

        assertTrue(summary.startsWith("Из 988 топ-игроков мирового рейтинга Clash Royale в рейтинговом режиме карту "
                + FSI + "Всадник на кабане" + PDI + " используют 2 игрока."), summary);
    }
}
