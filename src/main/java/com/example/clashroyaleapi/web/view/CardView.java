package com.example.clashroyaleapi.web.view;

/** テンプレートに渡す時点でカード名は表示用に解決済みにする(alt属性も同じ名前で揃う)。 */
public record CardView(String name, String iconUrl, int level) {
}
