package com.example.clashroyaleapi.web.view;

/**
 * 日時の表示。iso は &lt;time datetime&gt; に入れ、ブラウザ側で閲覧者のタイムゾーンの表記に書き換える。
 * text はJavaScriptが動かない場合にそのまま見える、表示言語の書式によるUTC表記。
 * APIの値を解釈できなかった場合、iso は null で text は元の値になる。
 */
public record TimeView(String iso, String text) {
}
