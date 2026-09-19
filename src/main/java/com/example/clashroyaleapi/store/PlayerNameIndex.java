package com.example.clashroyaleapi.store;

import com.example.clashroyaleapi.config.PlayerIndexProperties;
import com.example.clashroyaleapi.domain.GameText;
import com.example.clashroyaleapi.domain.PlayerNameMatch;
import com.example.clashroyaleapi.domain.PlayerNameSearch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.Normalizer;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.regex.Pattern;

/**
 * 名前検索用の索引 by-name/ を引く(「プレイヤー名検索（検討中）.md」参照)。索引を作るのは {@link PlayerNameIndexWriter}。
 * <ul>
 *   <li>行は「正規化名 \t tag \t 名前 \t 最終確認日時」。全体を行の辞書順(=正規化名の順)に並べ、数千〜2万行ずつのファイルに区切る</li>
 *   <li>index.tsv … 各ファイルの名前と先頭の行のキー(正規化名 \t tag)。前方一致で探す位置を二分探索で決めるのに使う</li>
 * </ul>
 * 同じ文字列で始まる行は必ず連続して並ぶので、完全一致も前方一致も、位置を決めてから順に読むだけで済む。
 */
@Component
public class PlayerNameIndex {

    static final String INDEX_FILE = "index.tsv";

    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Comparator<PlayerNameMatch> RECENT_FIRST = Comparator
            .comparing(PlayerNameMatch::lastSeen, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(PlayerNameMatch::tag);

    private final Path byNameDir;
    private volatile CachedIndex cachedIndex;

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

    /**
     * 正規化後に完全一致した人は全員を数え、最近確認した順の offset 番目から limit 人を返す。
     * 完全一致が limit 人未満なら、残りを前方一致の人(名前順)で埋める。
     */
    public PlayerNameSearch search(String name, int offset, int limit) {
        String key = normalize(name);
        if (key.isEmpty()) {
            return new PlayerNameSearch(List.of(), 0, offset, List.of(), false);
        }
        List<IndexEntry> entries = entries();
        String exactPrefix = key + "\t";

        // 並べ替えに必要な上位 offset+limit 人だけを残す(完全一致が数十万人いてもメモリを食わないように)。
        int keep = offset + limit;
        PriorityQueue<PlayerNameMatch> top = new PriorityQueue<>(RECENT_FIRST.reversed());
        int[] exactTotal = {0};
        scan(entries, exactPrefix, exactPrefix, line -> {
            exactTotal[0]++;
            top.add(toMatch(line));
            if (top.size() > keep) {
                top.poll();
            }
            return true;
        });
        List<PlayerNameMatch> ranked = new ArrayList<>(top);
        ranked.sort(RECENT_FIRST);
        List<PlayerNameMatch> exact = offset < ranked.size() ? ranked.subList(offset, ranked.size()) : List.of();

        // 前方一致は名前順に必要な分だけ読んで止める。1人多く読むのは、表示しきれない人がいるかを知るため。
        int wanted = offset == 0 ? Math.max(limit - exactTotal[0], 0) : 0;
        List<PlayerNameMatch> prefix = new ArrayList<>();
        boolean[] more = {false};
        scan(entries, key, key, line -> {
            if (line.startsWith(exactPrefix)) {
                return true;
            }
            if (prefix.size() >= wanted) {
                more[0] = true;
                return false;
            }
            prefix.add(toMatch(line));
            return true;
        });
        return new PlayerNameSearch(List.copyOf(exact), exactTotal[0], offset, prefix, more[0]);
    }

    private interface LineVisitor {
        /** false を返すと読むのをやめる。 */
        boolean visit(String line);
    }

    /** from 以上で prefix から始まる行を、並び順に visitor へ渡す。 */
    private void scan(List<IndexEntry> entries, String from, String prefix, LineVisitor visitor) {
        for (int i = startFile(entries, from); i < entries.size(); i++) {
            try (BufferedReader reader = Files.newBufferedReader(byNameDir.resolve(entries.get(i).file()),
                    StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.compareTo(from) < 0) {
                        continue;
                    }
                    if (!line.startsWith(prefix) || !visitor.visit(line)) {
                        return;
                    }
                }
            } catch (NoSuchFileException e) {
                // 整理バッチが目次を差し替えた直後に、古いファイルが消されることがある。次の検索からは新しい目次を読む。
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** from を含み得る最初のファイル(先頭のキーが from 以下のうち最後のもの)。 */
    private static int startFile(List<IndexEntry> entries, String from) {
        int low = 0;
        int high = entries.size() - 1;
        int found = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (entries.get(mid).firstKey().compareTo(from) <= 0) {
                found = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return found;
    }

    private List<IndexEntry> entries() {
        Path file = byNameDir.resolve(INDEX_FILE);
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            CachedIndex cached = cachedIndex;
            if (cached != null && cached.modified().equals(attributes.lastModifiedTime())
                    && cached.size() == attributes.size()) {
                return cached.entries();
            }
            List<IndexEntry> entries = readIndex(byNameDir);
            cachedIndex = new CachedIndex(attributes.lastModifiedTime(), attributes.size(), entries);
            return entries;
        } catch (NoSuchFileException e) {
            return List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<IndexEntry> readIndex(Path byNameDir) throws IOException {
        List<IndexEntry> entries = new ArrayList<>();
        for (String line : Files.readAllLines(byNameDir.resolve(INDEX_FILE), StandardCharsets.UTF_8)) {
            String[] fields = line.split("\t", 2);
            if (fields.length == 2) {
                entries.add(new IndexEntry(fields[0], fields[1]));
            }
        }
        return List.copyOf(entries);
    }

    /** 行のキー(正規化名 \t tag)。同じ名前の別人は別の行になる。 */
    static String keyOf(String line) {
        int first = line.indexOf('\t');
        int second = line.indexOf('\t', first + 1);
        return second < 0 ? line : line.substring(0, second);
    }

    private static PlayerNameMatch toMatch(String line) {
        String[] fields = line.split("\t", -1);
        return new PlayerNameMatch(fields[1], fields.length > 2 ? fields[2] : "",
                fields.length > 3 ? parseInstant(fields[3]) : null);
    }

    private static Instant parseInstant(String text) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** firstKey はそのファイルの先頭の行のキー。 */
    record IndexEntry(String file, String firstKey) {
    }

    private record CachedIndex(FileTime modified, long size, List<IndexEntry> entries) {
    }
}
