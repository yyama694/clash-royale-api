package com.example.clashroyaleapi.crawler;

import com.example.clashroyaleapi.client.Tags;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 巡回するクラン・プレイヤーのタグを、周回単位でファイルに持つ待ち行列。
 * <ul>
 *   <li>queue.txt … 今の周回で回るタグ(1行1タグ、重複なし)</li>
 *   <li>offset … queue.txt のどこまで処理したか(バイト位置)。再起動しても続きから再開するため</li>
 *   <li>discovered.txt … 今の周回で見つけたタグ(重複あり)</li>
 *   <li>visited/shard-N.txt … 一度でも周回に入れたタグ。タグのハッシュ値で分けて持つ</li>
 * </ul>
 * 次の周回は「見つけたタグのうち、まだ回っていないもの」だけにする。回ったばかりのタグをすぐ回り直すと、
 * 応答がほぼ同じで重複ばかりになるため(クラン対戦の履歴は週単位でしか変わらない。実測で新規の割合が90%→0%に落ちた)。
 * 新しいタグが尽きたときだけ、全件を回り直す。
 * クランは数百万件、プレイヤーは数千万件になり得るため、全件をメモリに持たない。
 * 重複除去もタグのハッシュ値で shards 個に分けたファイルごとに行い、一度にメモリに載るのは1ファイル分だけにする。
 */
class TagQueue {

    private static final int BUFFER_SIZE = 1000;

    private final int shards;

    private final Path queueFile;
    private final Path offsetFile;
    private final Path discoveredFile;
    private final Path visitedDir;
    private final Path workDir;
    private final Deque<String> buffer = new ArrayDeque<>();
    private long offset;
    private long bufferEnd;

    TagQueue(Path dir, int shards) {
        this.shards = shards;
        this.queueFile = dir.resolve("queue.txt");
        this.offsetFile = dir.resolve("offset");
        this.discoveredFile = dir.resolve("discovered.txt");
        this.visitedDir = dir.resolve("visited");
        this.workDir = dir.resolve("work");
        try {
            Files.createDirectories(dir);
            offset = Files.exists(offsetFile) ? Long.parseLong(Files.readString(offsetFile).strip()) : 0;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NumberFormatException e) {
            offset = 0;
        }
        bufferEnd = offset;
    }

    /** 次に回すタグ。周回を終えたら次の周回を組み立てる。回ったタグも見つけたタグも無ければ空。 */
    synchronized Optional<String> peek() throws IOException {
        if (buffer.isEmpty()) {
            fill();
        }
        if (buffer.isEmpty()) {
            startNextRound();
            fill();
        }
        return Optional.ofNullable(buffer.peekFirst());
    }

    /** peek したタグを処理済みにする。処理中に落ちた場合は、再起動後にもう一度同じタグを回す。 */
    synchronized void done() throws IOException {
        String tag = buffer.pollFirst();
        if (tag == null) {
            return;
        }
        // タグはASCIIだけなので、1行のバイト数は文字数+改行1バイトになる。
        offset += tag.length() + 1;
        writeAtomically(offsetFile, Long.toString(offset));
    }

    synchronized void discover(Collection<String> tags) throws IOException {
        if (tags.isEmpty()) {
            return;
        }
        try (Writer writer = Files.newBufferedWriter(discoveredFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            for (String tag : tags) {
                writer.write(Tags.normalize(tag) + "\n");
            }
        }
    }

    private void fill() throws IOException {
        if (!Files.exists(queueFile)) {
            return;
        }
        try (SeekableByteChannel channel = Files.newByteChannel(queueFile)) {
            channel.position(bufferEnd);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(Channels.newInputStream(channel), StandardCharsets.US_ASCII));
            String line;
            while (buffer.size() < BUFFER_SIZE && (line = reader.readLine()) != null) {
                // queue.txt はこのクラスだけが書き、空行を含まないので、1行=1タグとして扱える。
                bufferEnd += line.length() + 1;
                buffer.addLast(line);
            }
        }
    }

    /**
     * 見つけたタグのうち、まだ回っていないものを次の周回にする。1つも無ければ、回ったことのある全件を回り直す。
     * 途中で落ちても discovered.txt は残るので、次に呼ばれたときにやり直せる(最悪でも同じタグを2度回すだけ)。
     */
    private void startNextRound() throws IOException {
        Files.createDirectories(workDir);
        Files.createDirectories(visitedDir);
        Path[] shardFiles = splitDiscovered();

        Path next = workDir.resolve("queue.next");
        boolean found = false;
        try (Writer writer = Files.newBufferedWriter(next, StandardCharsets.US_ASCII)) {
            for (int i = 0; i < shards; i++) {
                Path visitedShard = visitedDir.resolve("shard-" + i + ".txt");
                Set<String> visited = Files.exists(visitedShard)
                        ? new HashSet<>(Files.readAllLines(visitedShard, StandardCharsets.US_ASCII))
                        : new HashSet<>();
                List<String> unvisited = new ArrayList<>();
                for (String tag : Files.readAllLines(shardFiles[i], StandardCharsets.US_ASCII)) {
                    if (visited.add(tag)) {
                        unvisited.add(tag);
                    }
                }
                for (String tag : unvisited) {
                    writer.write(tag + "\n");
                }
                appendLines(workDir.resolve("visited-add-" + i + ".txt"), unvisited);
                found |= !unvisited.isEmpty();
                Files.delete(shardFiles[i]);
            }
            if (!found) {
                for (int i = 0; i < shards; i++) {
                    Path visitedShard = visitedDir.resolve("shard-" + i + ".txt");
                    if (Files.exists(visitedShard)) {
                        for (String tag : Files.readAllLines(visitedShard, StandardCharsets.US_ASCII)) {
                            writer.write(tag + "\n");
                        }
                    }
                }
            }
        }
        // 位置を先に戻す。入れ替え前に落ちても古い周回を頭から回し直すだけで、タグを飛ばすことはない。
        offset = 0;
        bufferEnd = 0;
        writeAtomically(offsetFile, "0");
        Files.move(next, queueFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        // 回ったことの記録は、次の周回を確定させた後に反映する。先に反映して落ちると、回らないまま回った扱いになる。
        for (int i = 0; i < shards; i++) {
            Path added = workDir.resolve("visited-add-" + i + ".txt");
            if (Files.exists(added)) {
                appendLines(visitedDir.resolve("shard-" + i + ".txt"),
                        Files.readAllLines(added, StandardCharsets.US_ASCII));
                Files.delete(added);
            }
        }
        Files.deleteIfExists(discoveredFile);
    }

    private Path[] splitDiscovered() throws IOException {
        Path[] shardFiles = new Path[shards];
        Writer[] writers = new Writer[shards];
        try {
            for (int i = 0; i < shards; i++) {
                shardFiles[i] = workDir.resolve("shard-" + i + ".txt");
                writers[i] = Files.newBufferedWriter(shardFiles[i], StandardCharsets.US_ASCII);
            }
            if (Files.exists(discoveredFile)) {
                try (BufferedReader reader = Files.newBufferedReader(discoveredFile, StandardCharsets.US_ASCII)) {
                    String tag;
                    while ((tag = reader.readLine()) != null) {
                        if (!tag.isBlank()) {
                            writers[Math.floorMod(tag.hashCode(), shards)].write(tag + "\n");
                        }
                    }
                }
            }
        } finally {
            for (Writer writer : writers) {
                if (writer != null) {
                    writer.close();
                }
            }
        }
        return shardFiles;
    }

    private static void appendLines(Path file, List<String> lines) throws IOException {
        if (lines.isEmpty()) {
            return;
        }
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.US_ASCII,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            for (String line : lines) {
                writer.write(line + "\n");
            }
        }
    }

    private void writeAtomically(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
