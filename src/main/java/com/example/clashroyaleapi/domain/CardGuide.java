package com.example.clashroyaleapi.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * カード詳細画面に出す、手で書いた解説(cards/guides/&lt;言語&gt;/&lt;カードID&gt;.txt の1ファイル分)。
 *
 * <p>ファイルの書き方:
 * <pre>
 * # コメント(「## 」以外の「#」で始まる行は読み飛ばす)
 * title: 画面のタイトル(検索結果に出る)
 * heading: 解説の欄の見出し
 * note: 欄の下に出す断り書き(書いた日と根拠)
 * ## 小見出し
 * 1行が1段落。空行は読み飛ばす
 * - 続いた「- 」の行は1つの箇条書き
 * </pre>
 *
 * @param title    ページのタイトル。解説のあるカードだけ、ふだんのタイトルの代わりに使う
 * @param heading  解説の欄の見出し
 * @param sections 小見出しごとの中身(1つ以上)
 * @param note     欄の下の断り書き
 */
public record CardGuide(String title, String heading, List<Section> sections, String note) {

    public record Section(String heading, List<Block> blocks) {
    }

    /** 段落(items が空)か箇条書き(text が null)。 */
    public record Block(String text, List<String> items) {

        public boolean list() {
            return text == null;
        }
    }

    /** @throws IllegalArgumentException 書き方が違うとき(起動時に気づけるよう、黙って読み飛ばさない) */
    public static CardGuide parse(List<String> lines) {
        String title = null;
        String heading = null;
        String note = null;
        List<Section> sections = new ArrayList<>();
        String sectionHeading = null;
        List<Block> blocks = new ArrayList<>();
        List<String> items = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || (line.startsWith("#") && !line.startsWith("## "))) {
                continue;
            }
            if (!line.startsWith("- ") && !items.isEmpty()) {
                blocks.add(new Block(null, List.copyOf(items)));
                items.clear();
            }
            if (line.startsWith("## ")) {
                if (sectionHeading != null) {
                    sections.add(section(sectionHeading, blocks));
                    blocks.clear();
                }
                sectionHeading = line.substring(3).strip();
            } else if (sectionHeading == null) {
                int colon = line.indexOf(':');
                String key = colon < 0 ? "" : line.substring(0, colon).strip();
                String value = colon < 0 ? "" : line.substring(colon + 1).strip();
                switch (key) {
                    case "title" -> title = value;
                    case "heading" -> heading = value;
                    case "note" -> note = value;
                    default -> throw new IllegalArgumentException("unknown header line: " + line);
                }
            } else if (line.startsWith("- ")) {
                items.add(line.substring(2).strip());
            } else {
                blocks.add(new Block(line, List.of()));
            }
        }
        if (!items.isEmpty()) {
            blocks.add(new Block(null, List.copyOf(items)));
        }
        if (sectionHeading != null) {
            sections.add(section(sectionHeading, blocks));
        }
        if (isBlank(title) || isBlank(heading) || isBlank(note) || sections.isEmpty()) {
            throw new IllegalArgumentException("title, heading, note and at least one section are required");
        }
        return new CardGuide(title, heading, List.copyOf(sections), note);
    }

    private static Section section(String heading, List<Block> blocks) {
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException("empty section: " + heading);
        }
        return new Section(heading, List.copyOf(blocks));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
