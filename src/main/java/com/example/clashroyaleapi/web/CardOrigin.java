package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.client.Tags;

import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * カード詳細画面をどの画面から開いたか。戻るリンクの行き先に使う。
 * 戻り先のURLそのものは受け取らず(オープンリダイレクト対策)、タグと対戦日時だけをクエリで受け渡す。
 * どちらもnullならトップページに戻る。
 */
public record CardOrigin(String player, String battle) {

    private static final CardOrigin NONE = new CardOrigin(null, null);

    /** 公式APIのbattleTimeの形式(例: 20260918T201315.000Z)。 */
    private static final Pattern BATTLE_TIME = Pattern.compile("\\d{8}T\\d{6}\\.\\d{3}Z");

    public static CardOrigin none() {
        return NONE;
    }

    public static CardOrigin player(String tag) {
        return new CardOrigin(Tags.toPathSegment(tag), null);
    }

    public static CardOrigin battle(String tag, String battleTime) {
        return new CardOrigin(Tags.toPathSegment(tag), battleTime);
    }

    /** クエリから復元する。形式に合わない値は、壊れたリンクを出すよりトップに戻す方がよいので捨てる。 */
    public static CardOrigin fromQuery(String player, String battle) {
        if (player == null || !Tags.looksLikeTag(player)) {
            return NONE;
        }
        if (battle == null || !BATTLE_TIME.matcher(battle).matches()) {
            return player(player);
        }
        return battle(player, battle);
    }

    /** このoriginを付けたカード詳細画面へのパス。nullの項目はクエリに出さない(空の「battle=」を付けないため)。 */
    public String cardPath(int cardId) {
        return UriComponentsBuilder.fromPath("/card/{id}")
                .queryParamIfPresent("player", Optional.ofNullable(player))
                .queryParamIfPresent("battle", Optional.ofNullable(battle))
                .buildAndExpand(cardId).encode().toUriString();
    }

    public String backPath() {
        if (player == null) {
            return "/";
        }
        UriComponentsBuilder path = UriComponentsBuilder.fromPath("/player/{tag}");
        if (battle != null) {
            return path.path("/battles/{battleTime}").buildAndExpand(player, battle).encode().toUriString();
        }
        return path.buildAndExpand(player).encode().toUriString();
    }

    public String backLabelKey() {
        if (player == null) {
            return "action.backToTop";
        }
        return battle == null ? "action.backToPlayer" : "action.backToBattle";
    }
}
