package com.example.clashroyaleapi.store;

import com.example.clashroyaleapi.store.PlayerNameIndex.IndexEntry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * by-name/ に差分を反映する(形式は {@link PlayerNameIndex} を参照)。
 * ファイルは上書きせず、変わる範囲を新しい名前のファイルに書いてから目次 index.tsv を差し替え、最後に古いファイルを消す。
 * 検索は目次を読んでからファイルを読むので、書き換えの途中の状態を読むことがない。
 * 途中で落ちても目次は前のままなので、索引は前回の状態で使える(書きかけのファイルは次回消す)。
 */
final class PlayerNameIndexWriter {

    // ファイルがこの2倍に達したら、この行数ずつに分ける。1ファイル1〜2万行・1〜2MBなら、検索で読む量は数MBで済む。
    static final int DEFAULT_CHUNK_LINES = 10_000;

    private final Path byNameDir;
    private final int chunkLines;

    PlayerNameIndexWriter(Path byNameDir, int chunkLines) {
        this.byNameDir = byNameDir;
        this.chunkLines = chunkLines;
    }

    /**
     * 差分を反映し、新しく書いたファイル数を返す。差分の行は「正規化名 \t tag \t + \t 名前 \t 最終確認日時」(追加・更新)と
     * 「正規化名 \t tag \t -」(削除)。fromScratch なら今の索引を捨て、差分(すべて追加)だけで作る。
     * unsortedDiffs は並べ替えの途中で消える。
     */
    int apply(Path unsortedDiffs, Path workDir, boolean fromScratch, String runId) throws IOException {
        Files.createDirectories(byNameDir);
        Path diffs = ExternalSort.sort(unsortedDiffs, workDir);
        List<IndexEntry> current = fromScratch || !Files.exists(byNameDir.resolve(PlayerNameIndex.INDEX_FILE))
                ? List.of()
                : PlayerNameIndex.readIndex(byNameDir);
        List<IndexEntry> next = new ArrayList<>();
        ChunkWriter out = new ChunkWriter(runId);
        try (DiffReader diff = new DiffReader(diffs)) {
            if (current.isEmpty()) {
                merge(null, diff, null, out);
                next.addAll(out.finish());
            }
            for (int i = 0; i < current.size(); i++) {
                // 先頭のファイルは、目次の先頭のキーより前に来る差分も受け持つ。
                String bound = i + 1 < current.size() ? current.get(i + 1).firstKey() : null;
                if (diff.hasKeyBefore(bound)) {
                    merge(byNameDir.resolve(current.get(i).file()), diff, bound, out);
                    next.addAll(out.finish());
                } else {
                    next.add(current.get(i));
                }
            }
        }
        Files.delete(diffs);
        writeIndex(next);
        deleteUnreferenced(next);
        return out.sequence;
    }

    /** 並んだ既存の行と、bound より前の並んだ差分を突き合わせて out に流す。 */
    private static void merge(Path base, DiffReader diff, String bound, ChunkWriter out) throws IOException {
        try (BufferedReader reader = base == null ? null : Files.newBufferedReader(base, StandardCharsets.UTF_8)) {
            String row = reader == null ? null : reader.readLine();
            while (true) {
                boolean hasDiff = diff.hasKeyBefore(bound);
                if (row == null && !hasDiff) {
                    return;
                }
                int order = row == null ? 1 : !hasDiff ? -1 : PlayerNameIndex.keyOf(row).compareTo(diff.peekKey());
                if (order < 0) {
                    out.add(row);
                    row = reader.readLine();
                    continue;
                }
                String replacement = diff.next();
                if (order == 0) {
                    row = reader.readLine();
                }
                if (replacement != null) {
                    out.add(replacement);
                }
            }
        }
    }

    private void writeIndex(List<IndexEntry> entries) throws IOException {
        Path target = byNameDir.resolve(PlayerNameIndex.INDEX_FILE);
        Path tmp = byNameDir.resolve(PlayerNameIndex.INDEX_FILE + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            for (IndexEntry entry : entries) {
                writer.write(entry.file() + "\t" + entry.firstKey() + "\n");
            }
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    // 以前の版の索引(ハッシュで分けた0000.tsv〜4095.tsv)や、落ちたときの書きかけのファイルもここで消える。
    private void deleteUnreferenced(List<IndexEntry> entries) throws IOException {
        Set<String> referenced = new HashSet<>();
        entries.forEach(entry -> referenced.add(entry.file()));
        referenced.add(PlayerNameIndex.INDEX_FILE);
        try (Stream<Path> files = Files.list(byNameDir)) {
            for (Path file : files.toList()) {
                if (!referenced.contains(file.getFileName().toString())) {
                    Files.delete(file);
                }
            }
        }
    }

    /**
     * 並んだ行を受け取り、ファイルに区切って書く。chunkLines の2倍未満ならそのまま1ファイルにし、
     * それ以上なら chunkLines 行ずつに分ける(最後のファイルも chunkLines 行以上になる)。
     * 1行ずつ増えるたびに分けると、ほとんど空のファイルが増えていくため。
     */
    private final class ChunkWriter {
        private final String runId;
        private final List<String> buffer = new ArrayList<>();
        private final List<IndexEntry> written = new ArrayList<>();
        private int sequence;

        ChunkWriter(String runId) {
            this.runId = runId;
        }

        void add(String row) throws IOException {
            buffer.add(row);
            if (buffer.size() >= 2 * chunkLines) {
                write(buffer.subList(0, chunkLines));
            }
        }

        List<IndexEntry> finish() throws IOException {
            if (!buffer.isEmpty()) {
                write(buffer);
            }
            List<IndexEntry> result = List.copyOf(written);
            written.clear();
            return result;
        }

        private void write(List<String> rows) throws IOException {
            String file = String.format("%s-%06d.tsv", runId, sequence++);
            Files.write(byNameDir.resolve(file), rows, StandardCharsets.UTF_8);
            written.add(new IndexEntry(file, PlayerNameIndex.keyOf(rows.get(0))));
            rows.clear();
        }
    }

    /** 並んだ差分を先読みしながら読む。next() は追加・更新なら索引の1行を、削除なら null を返す。 */
    private static final class DiffReader implements AutoCloseable {
        private final BufferedReader reader;
        private String line;

        DiffReader(Path file) throws IOException {
            this.reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
            this.line = reader.readLine();
        }

        boolean hasKeyBefore(String bound) {
            return line != null && (bound == null || peekKey().compareTo(bound) < 0);
        }

        String peekKey() {
            return PlayerNameIndex.keyOf(line);
        }

        String next() throws IOException {
            String key = peekKey();
            String rest = line.substring(key.length() + 1);
            line = reader.readLine();
            return rest.startsWith("+\t") ? key + "\t" + rest.substring(2) : null;
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }
}
