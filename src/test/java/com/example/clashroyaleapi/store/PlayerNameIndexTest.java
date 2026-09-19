package com.example.clashroyaleapi.store;

import com.example.clashroyaleapi.domain.PlayerNameMatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerNameIndexTest {

    @TempDir
    Path dir;

    @Test
    void 正規化は装飾タグ_全角半角_大文字小文字_空白の違いを吸収する() {
        // ルールを変えると索引の作り直しになるので、ここで固定する。
        assertEquals("abc 1", PlayerNameIndex.normalize("<c2>ＡＢＣ　 １"));
        assertEquals("taro yamada", PlayerNameIndex.normalize("  Taro   YAMADA "));
        assertEquals("かいと", PlayerNameIndex.normalize("かいと"));
        assertEquals("カイト", PlayerNameIndex.normalize("ｶｲﾄ"));
        assertEquals("", PlayerNameIndex.normalize(null));
    }

    @Test
    void 完全一致した人を最後に確認した日時の新しい順に返す() throws IOException {
        byName("bob",
                "bob\t#AAA\tBob\t2026-09-18T10:00:00Z",
                "bob\t#BBB\t<c1>BOB\t2026-09-19T10:00:00Z",
                "bobby\t#CCC\tBobby\t2026-09-19T11:00:00Z");

        List<PlayerNameMatch> matches = new PlayerNameIndex(dir).find(" BOB ");

        assertEquals(List.of(
                new PlayerNameMatch("#BBB", "<c1>BOB", Instant.parse("2026-09-19T10:00:00Z")),
                new PlayerNameMatch("#AAA", "Bob", Instant.parse("2026-09-18T10:00:00Z"))), matches);
    }

    @Test
    void 索引がまだ無ければ空を返す() {
        assertTrue(new PlayerNameIndex(dir).find("bob").isEmpty());
        assertTrue(new PlayerNameIndex(dir).find("  ").isEmpty());
    }

    private void byName(String normalizedName, String... lines) throws IOException {
        Path byNameDir = dir.resolve("by-name");
        Files.createDirectories(byNameDir);
        Files.write(PlayerNameIndex.shardFile(byNameDir, PlayerNameIndex.shardOf(normalizedName)), List.of(lines),
                StandardCharsets.UTF_8);
    }
}
