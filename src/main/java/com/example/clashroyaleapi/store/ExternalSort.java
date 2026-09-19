package com.example.clashroyaleapi.store;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * 行単位の外部ソート(メモリに載らない量の行を並べる方法)。
 * 一定行数ずつメモリで並べて一時ファイルに書き、それらを少しずつ併合して1つの並んだファイルにする。
 * 並び順は String#compareTo(検索側の比較と同じ)。
 */
final class ExternalSort {

    // 1回にメモリに載せる行数。索引の1行は100バイト前後なので、数十MBで収まる。
    private static final int LINES_PER_RUN = 50_000;
    // 同時に開くファイル数の上限(OSの上限1024に掛からないよう小さくする)。
    private static final int MERGE_FAN_IN = 32;

    private ExternalSort() {
    }

    /** input を並べたファイルを workDir に作って返す。ディスクを食わないよう、input と途中のファイルは消す。 */
    static Path sort(Path input, Path workDir) throws IOException {
        Files.createDirectories(workDir);
        List<Path> runs = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                if (lines.size() >= LINES_PER_RUN) {
                    runs.add(writeRun(lines, workDir.resolve("run-" + runs.size() + ".tsv")));
                }
            }
            if (!lines.isEmpty() || runs.isEmpty()) {
                runs.add(writeRun(lines, workDir.resolve("run-" + runs.size() + ".tsv")));
            }
        }
        Files.delete(input);

        for (int pass = 0; runs.size() > 1; pass++) {
            List<Path> merged = new ArrayList<>();
            for (int from = 0; from < runs.size(); from += MERGE_FAN_IN) {
                List<Path> group = runs.subList(from, Math.min(from + MERGE_FAN_IN, runs.size()));
                Path output = workDir.resolve("merge-" + pass + "-" + merged.size() + ".tsv");
                merge(group, output);
                for (Path run : group) {
                    Files.delete(run);
                }
                merged.add(output);
            }
            runs = merged;
        }
        return runs.get(0);
    }

    private static Path writeRun(List<String> lines, Path output) throws IOException {
        lines.sort(Comparator.naturalOrder());
        Files.write(output, lines, StandardCharsets.UTF_8);
        lines.clear();
        return output;
    }

    private static void merge(List<Path> inputs, Path output) throws IOException {
        List<BufferedReader> readers = new ArrayList<>();
        try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            PriorityQueue<Head> heads = new PriorityQueue<>(Comparator.comparing(Head::line));
            for (Path input : inputs) {
                BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8);
                readers.add(reader);
                String line = reader.readLine();
                if (line != null) {
                    heads.add(new Head(line, reader));
                }
            }
            while (!heads.isEmpty()) {
                Head head = heads.poll();
                writer.write(head.line());
                writer.write('\n');
                String next = head.reader().readLine();
                if (next != null) {
                    heads.add(new Head(next, head.reader()));
                }
            }
        } finally {
            for (BufferedReader reader : readers) {
                reader.close();
            }
        }
    }

    private record Head(String line, BufferedReader reader) {
    }
}
