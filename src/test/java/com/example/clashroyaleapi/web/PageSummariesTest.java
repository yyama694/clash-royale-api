package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.config.IcuMessageSource;
import com.example.clashroyaleapi.domain.ClanWarParticipation;
import com.example.clashroyaleapi.domain.PlayerBattleStats;
import com.example.clashroyaleapi.domain.WinLoseStreak;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 実際の messages*.properties を読んで、組み立てた文を確かめる。 */
class PageSummariesTest {

    // 名前の前後の不可視文字(FSI/PDI)。右から左に書く言語の名前でも前後の並びを崩さないためのもの。
    private static final String FSI = "⁨";
    private static final String PDI = "⁩";

    private final PageSummaries summaries = new PageSummaries(
            new LabelResolver(IcuMessageSource.forBasename("messages")),
            Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC));

    private static PlayerResponse player(PlayerResponse.RankedSeasonResult ranked) {
        return new PlayerResponse("#ABC123", "Taro", 50, 9000, 9200, 3210, 1000, 500, null, List.of(), List.of(),
                null, ranked, null, List.of());
    }

    private static PlayerBattleStats stats(int wins, int losses) {
        return new PlayerBattleStats(wins + losses, wins, losses, 0, 0, List.of(), List.of());
    }

    @Test
    void プレイヤーのタイトルにタグと検索で打たれる語を入れる() {
        assertEquals(FSI + "Taro" + PDI + "(#ABC123)のクラロワ戦績・デッキ",
                summaries.playerTitle(player(null), "Taro", Locale.JAPANESE));
    }

    @Test
    void プレイヤーの要約はある情報だけを文にしてつなぐ() {
        String summary = summaries.playerSummary(player(new PlayerResponse.RankedSeasonResult(7, 2800, 12)), "Taro",
                new WinLoseStreak(true, 5), 3.125, stats(18, 7), Locale.JAPANESE);

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
        String summary = summaries.playerSummary(player(null), "Taro", new WinLoseStreak(false, 1), null, stats(1, 2),
                Locale.ENGLISH);

        assertEquals("Clash Royale player " + FSI + "Taro" + PDI + " (#ABC123) has 9,000 trophies and 3,210 wins in total."
                + " Currently on 1 loss in a row. Recent battles: 1 win and 2 losses.", summary);
    }

    @Test
    void ロシア語の複数形() {
        String summary = summaries.playerSummary(player(null), "Taro", new WinLoseStreak(true, 3), null, null,
                Locale.forLanguageTag("ru"));

        assertEquals("Игрок Clash Royale " + FSI + "Taro" + PDI + " (#ABC123): 9 000 трофеев и 3 210 побед всего."
                + " Сейчас 3 победы подряд.", summary.replace(' ', ' '));
    }

    @Test
    void クランの要約に活動中のメンバー数とクラン対戦の状況を入れる() {
        List<ClanResponse.Member> members = List.of(
                new ClanResponse.Member("#A", "a", "leader", 9000, 100, "20260927T010000.000Z"),
                new ClanResponse.Member("#B", "b", "member", 9000, 0, "20260926T010000.000Z"),
                new ClanResponse.Member("#C", "c", "member", 9000, 0, "20260901T010000.000Z"));
        ClanResponse clan = new ClanResponse("#CLAN1", "Clan", "", 133292, 0, 3, members);
        ClanWarParticipation war = new ClanWarParticipation(true, List.of(
                new ClanWarParticipation.Member("#A", "a", 4, 12),
                new ClanWarParticipation.Member("#B", "b", 0, 8),
                new ClanWarParticipation.Member("#C", "c", 0, 0)));

        assertEquals(FSI + "Clan" + PDI + "(#CLAN1)はクラロワのクランです。クランスコア133,292、メンバー3/50人。"
                        + "直近7日以内にアクセスしたメンバーは3人中2人。今日のクラン対戦は3人中1人が攻撃済み。",
                summaries.clanSummary(clan, "Clan", war, Locale.JAPANESE));
    }

    @Test
    void クラン対戦の攻撃できない日と不参加なら対戦の文を省く() {
        ClanResponse clan = new ClanResponse("#CLAN1", "Clan", "", 100, 0, 0, List.of());
        String expected = FSI + "Clan" + PDI + "(#CLAN1)はクラロワのクランです。クランスコア100、メンバー0/50人。";

        assertEquals(expected, summaries.clanSummary(clan, "Clan", null, Locale.JAPANESE));
        assertEquals(expected, summaries.clanSummary(clan, "Clan", new ClanWarParticipation(false, List.of()),
                Locale.JAPANESE));
    }
}
