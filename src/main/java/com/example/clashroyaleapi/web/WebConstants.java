package com.example.clashroyaleapi.web;

public final class WebConstants {

    /** その画面の表示言語を指定するクエリパラメータ名(Cookieには保存しない)。言語を保存するCookie名にも使う。 */
    public static final String LANGUAGE_PARAM = "lang";

    /** 言語メニューで選んだ言語をCookieに保存するクエリパラメータ名。保存したら ?lang= のURLへ転送する。 */
    public static final String SAVE_LANGUAGE_PARAM = "setlang";

    /** 国別ランキングの対象国の切り替えに使うクエリパラメータ名。Cookie名にも同じ名前を使う。 */
    public static final String COUNTRY_PARAM = "country";

    /** X等に出したURLに付けている流入元タグ(?from=x など)。表示の計測はビーコンが行う。 */
    public static final String SOURCE_PARAM = "from";

    /**
     * 同じページをXに貼り直すときにカードを作り直させる版(?v=1015 など)。Xは一度作ったカードを覚えていて、
     * 中身が変わるページ(ランキングの動きなど)を2回目に貼ると前回のカードのままになるため。og:urlにだけ残す。
     */
    public static final String CARD_VERSION_PARAM = "v";

    private WebConstants() {
    }
}
