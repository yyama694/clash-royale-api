package com.example.clashroyaleapi.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProPlayerTest {

    private static final String SOURCE = "https://royaleapi.com/player/G9YV9GR8R";

    @Test
    void タグを揃え_空の所属とチャンネルはnullにして読む() {
        List<ProPlayer> players = ProPlayer.parse(List.of(
                "# コメント",
                "g9yv9gr8r\tMohamed Light\tpro\tEG\tSKCalalas\thttps://www.youtube.com/channel/UC1\t" + SOURCE,
                "",
                "9VVCQYC9Y\tEkra (エクラ)\tcreator\tJP\t\t\t" + SOURCE));

        assertEquals(2, players.size());
        ProPlayer light = players.get(0);
        assertEquals("#G9YV9GR8R", light.tag());
        assertEquals(ProPlayer.Kind.PRO, light.kind());
        assertEquals("SKCalalas", light.team());
        assertEquals("YouTube", light.channelService());
        ProPlayer ekra = players.get(1);
        assertEquals(ProPlayer.Kind.CREATOR, ekra.kind());
        assertNull(ekra.team());
        assertNull(ekra.channel());
        assertNull(ekra.channelService());
    }

    @Test
    void チャンネルのサービス名はホスト名から決める() {
        assertEquals("Twitch", channelOf("https://www.twitch.tv/someone").channelService());
        assertEquals("X", channelOf("https://x.com/someone").channelService());
        assertEquals("X", channelOf("https://twitter.com/someone").channelService());
    }

    @Test
    void 書き間違いは読み飛ばさずに例外にする() {
        List<String> bad = List.of(
                "G9YV9GR8R\tMohamed Light\tstreamer\tEG\t\t\t" + SOURCE,
                "G9YV9GR8R\tMohamed Light\tpro\tEgypt\t\t\t" + SOURCE,
                "G9YV9GR8R\t\tpro\tEG\t\t\t" + SOURCE,
                "G9YV9GR8R\tMohamed Light\tpro\tEG\t\t\t",
                "G9YV9GR8R\tMohamed Light\tpro\tEG\t\thttp://www.youtube.com/channel/UC1\t" + SOURCE,
                "G9YV9GR8R\tMohamed Light\tpro\tEG\t\thttps://example.com/light\t" + SOURCE,
                "G9YV9GR8R\tMohamed Light\tpro\tEG\tSKCalalas",
                "名前\tMohamed Light\tpro\tEG\t\t\t" + SOURCE);
        for (String line : bad) {
            assertThrows(IllegalArgumentException.class, () -> ProPlayer.parse(List.of(line)), line);
        }
    }

    @Test
    void 同じタグを2度書いたら例外にする() {
        assertThrows(IllegalArgumentException.class, () -> ProPlayer.parse(List.of(
                "G9YV9GR8R\tMohamed Light\tpro\tEG\t\t\t" + SOURCE,
                "g9yv9gr8r\tLight\tpro\tEG\t\t\t" + SOURCE)));
    }

    @Test
    void 本番の一覧を読める() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("pros/players.tsv")) {
            List<ProPlayer> players = ProPlayer.parse(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList());
            assertFalse(players.isEmpty());
        }
    }

    private static ProPlayer channelOf(String url) {
        return ProPlayer.parse(List.of("G9YV9GR8R\tMohamed Light\tpro\tEG\t\t" + url + "\t" + SOURCE)).getFirst();
    }
}
