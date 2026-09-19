package com.example.clashroyaleapi.crawler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TagQueueTest {

    @TempDir
    Path dir;

    @Test
    void 回ったタグも見つけたタグも無ければ空() throws IOException {
        assertEquals(Optional.empty(), new TagQueue(dir, 4).peek());
    }

    @Test
    void 見つけたタグは重複を除いて次の周回で回る() throws IOException {
        TagQueue queue = new TagQueue(dir, 4);
        queue.discover(List.of("#AAA", "#BBB", "#aaa"));

        assertEquals(List.of("#AAA", "#BBB"), take(queue, 2));
    }

    @Test
    void 回ったことのあるタグは新しいタグがある間は回り直さない() throws IOException {
        TagQueue queue = new TagQueue(dir, 4);
        queue.discover(List.of("#AAA"));
        assertEquals(List.of("#AAA"), take(queue, 1));

        queue.discover(List.of("#AAA", "#BBB"));

        assertEquals(List.of("#BBB"), take(queue, 1));
    }

    @Test
    void 新しいタグが尽きたら回ったことのある全件を回り直す() throws IOException {
        TagQueue queue = new TagQueue(dir, 4);
        queue.discover(List.of("#AAA", "#BBB"));
        take(queue, 2);

        assertEquals(List.of("#AAA", "#BBB"), take(queue, 2));
    }

    @Test
    void 再起動しても処理済みの続きから再開する() throws IOException {
        TagQueue queue = new TagQueue(dir, 4);
        queue.discover(List.of("#AAA", "#BBB", "#CCC"));
        String first = queue.peek().orElseThrow();
        queue.done();
        String second = queue.peek().orElseThrow();

        TagQueue restarted = new TagQueue(dir, 4);

        assertEquals(second, restarted.peek().orElseThrow());
        List<String> rest = take(restarted, 2);
        assertFalse(rest.contains(first));
    }

    /** 周回をまたいで先頭から count 件を処理し、並べ替えて返す(周回内の順序はハッシュ値で分けた順になるため)。 */
    static List<String> take(TagQueue queue, int count) throws IOException {
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tags.add(queue.peek().orElseThrow());
            queue.done();
        }
        return tags.stream().sorted().toList();
    }
}
