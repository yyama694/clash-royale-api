package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.Tags;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * プロ・クリエイターの一覧({@code /pros})に載せる人。{@code pros/players.tsv} に手で書く。
 * 載せるのは、タグがその人のものだと確かめられた人だけ(なりすましの別人を載せないため)。
 *
 * @param tag     "#" 付きに揃えたタグ
 * @param name    活動名。ゲーム内の名前と違うことがある
 * @param country 国・地域のコード(ISO 3166-1 alpha-2)
 * @param team    所属チーム。無ければ null
 * @param channel 本人のチャンネル(YouTube・Twitch・Xなど)。無ければ null
 */
public record ProPlayer(String tag, String name, Kind kind, String country, String team, URI channel) {

    public enum Kind {
        PRO, CREATOR;

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** チャンネルのサービス名。リンクの文字に使う。知らないサービスは null。 */
    public String channelService() {
        if (channel == null || channel.getHost() == null) {
            return null;
        }
        String host = channel.getHost().toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        return switch (host) {
            case "youtube.com" -> "YouTube";
            case "twitch.tv" -> "Twitch";
            case "x.com", "twitter.com" -> "X";
            case "tiktok.com" -> "TikTok";
            case "kick.com" -> "Kick";
            default -> null;
        };
    }

    /**
     * 書式: タグ・活動名・種類(pro/creator)・国・所属チーム・チャンネル・確かめた所(URL)のタブ区切り。
     * 所属チームとチャンネルは空でよい。確かめた所は画面に出さない(後から確かめ直すための記録)。
     * 書き間違いは起動時に気付けるよう、黙って読み飛ばさずに例外にする。
     */
    public static List<ProPlayer> parse(List<String> lines) {
        List<ProPlayer> players = new ArrayList<>();
        Set<String> tags = new HashSet<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split("\t", -1);
            require(f.length == 7 && Tags.looksLikeTag(f[0]) && !f[1].isBlank() && f[3].matches("[A-Z]{2}")
                    && !f[6].isBlank(), line);
            String tag = Tags.normalize(f[0]);
            require(tags.add(tag), line);
            ProPlayer player = new ProPlayer(tag, f[1].strip(), kind(f[2], line), f[3],
                    f[4].isBlank() ? null : f[4].strip(), f[5].isBlank() ? null : channel(f[5], line));
            // 知らないサービスのチャンネルは画面にリンクが出ないので、書いた時点で気付けるようにする。
            require(player.channel() == null || player.channelService() != null, line);
            players.add(player);
        }
        return List.copyOf(players);
    }

    private static Kind kind(String value, String line) {
        try {
            return Kind.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("bad line in pros/players.tsv: " + line, e);
        }
    }

    private static URI channel(String value, String line) {
        URI uri = URI.create(value.strip());
        require("https".equals(uri.getScheme()), line);
        return uri;
    }

    private static void require(boolean condition, String line) {
        if (!condition) {
            throw new IllegalArgumentException("bad line in pros/players.tsv: " + line);
        }
    }
}
