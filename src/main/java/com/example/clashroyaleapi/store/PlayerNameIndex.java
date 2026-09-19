package com.example.clashroyaleapi.store;

import com.example.clashroyaleapi.config.PlayerIndexProperties;
import com.example.clashroyaleapi.domain.GameText;
import com.example.clashroyaleapi.domain.PlayerNameMatch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 名前検索用の索引 by-name/ を引く(「プレイヤー名検索（検討中）.md」参照)。索引を作るのは {@link PlayerIndexCompactor}。
 * <ul>
 *   <li>by-name/NNNN.tsv … 正規化名 \t tag \t 名前 \t 最終確認日時。正規化名のハッシュ値で4096個に分ける</li>
 * </ul>
 * 検索は正規化名から1ファイルを特定して読むだけなので、全体が数十GBになっても1ファイル分(数MB)で済む。
 */
@Component
public class PlayerNameIndex {

    // 変えると全件の振り分け直しになるので固定する。
    static final int NAME_SHARDS = 4096;

    private static final Pattern SPACES = Pattern.compile("\s+");

    private final Path byNameDir;

    @Autowired
    public PlayerNameIndex(PlayerIndexProperties properties) {
        this(Path.of(properties.dir()));
    }

    PlayerNameIndex(Path dir) {
        this.byNameDir = dir.resolve("by-name");
    }

    /**
     * 索引のキー。保存時と検索時で必ずこれを通す。
     * ルールを変えたら by-name の作り直しが要る(by-name を消せば、次の整理バッチが by-tag から作り直す)。
     */
    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String text = Normalizer.normalize(GameText.stripFormatting(name), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        return SPACES.matcher(text).replaceAll(" ").strip();
    }

    static int shardOf(String normalizedName) {
        return Math.floorMod(normalizedName.hashCode(), NAME_SHARDS);
    }

    static Path shardFile(Path byNameDir, int shard) {
        return byNameDir.resolve(String.format("%04d.tsv", shard));
    }

    /** 正規化後に完全一致した全員を、最後に確認した日時の新しい順に返す。 */
    public List<PlayerNameMatch> find(String name) {
        String key = normalize(name);
        if (key.isEmpty()) {
            return List.of();
        }
        String prefix = key + "\t";
        List<PlayerNameMatch> matches = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(shardFile(byNameDir, shardOf(key)),
                StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith(prefix)) {
                    continue;
                }
                String[] fields = line.split("\t", -1);
                if (fields.length == 4) {
                    matches.add(new PlayerNameMatch(fields[1], fields[2], parseInstant(fields[3])));
                }
            }
        } catch (NoSuchFileException e) {
            return List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        matches.sort(Comparator.comparing(PlayerNameMatch::lastSeen,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return matches;
    }

    private static Instant parseInstant(String text) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
