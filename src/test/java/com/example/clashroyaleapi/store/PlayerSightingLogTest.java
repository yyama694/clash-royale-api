package com.example.clashroyaleapi.store;

import com.example.clashroyaleapi.domain.PlayerSighting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PlayerSightingLogTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-18T10:15:30.123Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    @Test
    void 時刻ごとのファイルにタグと名前と確認日時を追記する() throws IOException {
        PlayerSightingLog sightingLog = new PlayerSightingLog(dir, CLOCK);

        sightingLog.record(List.of(new PlayerSighting("#abc", "Yamada")));
        sightingLog.flush();

        assertEquals(List.of("#ABC\tYamada\t2026-09-18T10:15:30Z"), lines("20260918-10.tsv"));
    }

    @Test
    void 最近書いたタグと名前の組は重複して書かない() throws IOException {
        PlayerSightingLog sightingLog = new PlayerSightingLog(dir, CLOCK);

        sightingLog.record(List.of(new PlayerSighting("#ABC", "Yamada"), new PlayerSighting("#ABC", "Yamada")));
        sightingLog.record(List.of(new PlayerSighting("#ABC", "Yamada")));
        sightingLog.flush();

        assertEquals(1, lines("20260918-10.tsv").size());
    }

    @Test
    void 名前が変わった場合は別の組として書く() throws IOException {
        PlayerSightingLog sightingLog = new PlayerSightingLog(dir, CLOCK);

        sightingLog.record(List.of(new PlayerSighting("#ABC", "Yamada")));
        sightingLog.record(List.of(new PlayerSighting("#ABC", "Tanaka")));
        sightingLog.flush();

        assertEquals(2, lines("20260918-10.tsv").size());
    }

    @Test
    void 名前の中のタブと改行は空白に置き換えて行を壊さない() throws IOException {
        PlayerSightingLog sightingLog = new PlayerSightingLog(dir, CLOCK);

        sightingLog.record(List.of(new PlayerSighting("#ABC", "a\tb\nc")));
        sightingLog.flush();

        assertEquals(List.of("#ABC\ta b c\t2026-09-18T10:15:30Z"), lines("20260918-10.tsv"));
    }

    @Test
    void 巡回で集めた分は別のファイルに重複を除かずに書く() throws IOException {
        PlayerSightingLog sightingLog = new PlayerSightingLog(dir, CLOCK);
        sightingLog.record(List.of(new PlayerSighting("#ABC", "Yamada")));

        sightingLog.recordCrawled(List.of(new PlayerSighting("#ABC", "Yamada")));
        sightingLog.recordCrawled(List.of(new PlayerSighting("#ABC", "Yamada")));
        sightingLog.flush();

        assertEquals(List.of("#ABC	Yamada	2026-09-18T10:15:30Z", "#ABC	Yamada	2026-09-18T10:15:30Z"),
                lines("crawl-20260918-10.tsv"));
    }

    @Test
    void タグか名前が空のものは書かない() throws IOException {
        PlayerSightingLog sightingLog = new PlayerSightingLog(dir, CLOCK);

        sightingLog.record(List.of(new PlayerSighting(null, "Yamada"), new PlayerSighting("#ABC", " ")));
        sightingLog.flush();

        assertFalse(Files.exists(dir.resolve("20260918-10.tsv")));
    }

    @Test
    void 書き込みに失敗しても例外を投げない() throws IOException {
        // ディレクトリを作るべき場所に同名のファイルがあると、ディレクトリ作成が失敗する。
        Path blocked = dir.resolve("inbox");
        Files.writeString(blocked, "");
        PlayerSightingLog sightingLog = new PlayerSightingLog(blocked, CLOCK);

        sightingLog.record(List.of(new PlayerSighting("#ABC", "Yamada")));
        sightingLog.flush();
    }

    @Test
    void recordはディスクに書かずキューに積むだけで即座に返る() throws IOException {
        PlayerSightingLog sightingLog = new PlayerSightingLog(dir, CLOCK);

        sightingLog.record(List.of(new PlayerSighting("#ABC", "Yamada")));

        assertFalse(Files.exists(dir.resolve("20260918-10.tsv")));
    }

    private List<String> lines(String fileName) throws IOException {
        return Files.readAllLines(dir.resolve(fileName), StandardCharsets.UTF_8);
    }
}
