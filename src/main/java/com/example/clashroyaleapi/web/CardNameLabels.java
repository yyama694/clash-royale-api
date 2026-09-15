package com.example.clashroyaleapi.web;

import java.util.Locale;
import java.util.Map;

/**
 * Clash Royale公式APIが返すカード名(英語)を、アクセス言語が日本語の場合のみ日本語名に変換する。
 * 対応表は日本語版クラロワの公式ローカライズ名に基づく(2026-09-14に複数の日本語攻略サイトを
 * 突き合わせて再調査・修正)。未知のカード名は英語名のままフォールバックする。
 */
public final class CardNameLabels {

    private static final Map<String, String> JAPANESE_LABELS = Map.ofEntries(
            Map.entry("Knight", "ナイト"),
            Map.entry("Archers", "アーチャー"),
            Map.entry("Goblins", "ゴブリン"),
            Map.entry("Giant", "ジャイアント"),
            Map.entry("P.E.K.K.A", "ペッカ"),
            Map.entry("Minions", "ガーゴイル"),
            Map.entry("Balloon", "エアバルーン"),
            Map.entry("Witch", "ネクロマンサー"),
            Map.entry("Barbarians", "バーバリアン"),
            Map.entry("Golem", "ゴーレム"),
            Map.entry("Skeletons", "スケルトン"),
            Map.entry("Valkyrie", "バルキリー"),
            Map.entry("Skeleton Army", "スケルトン部隊"),
            Map.entry("Bomber", "ボンバー"),
            Map.entry("Musketeer", "マスケット銃士"),
            Map.entry("Baby Dragon", "ベビードラゴン"),
            Map.entry("Prince", "プリンス"),
            Map.entry("Wizard", "ウィザード"),
            Map.entry("Mini P.E.K.K.A", "ミニペッカ"),
            Map.entry("Spear Goblins", "槍ゴブリン"),
            Map.entry("Giant Skeleton", "巨大スケルトン"),
            Map.entry("Hog Rider", "ホグライダー"),
            Map.entry("Minion Horde", "ガーゴイルの群れ"),
            Map.entry("Ice Wizard", "アイスウィザード"),
            Map.entry("Royal Giant", "ロイヤルジャイアント"),
            Map.entry("Guards", "盾の戦士"),
            Map.entry("Princess", "プリンセス"),
            Map.entry("Dark Prince", "ダークプリンス"),
            Map.entry("Three Musketeers", "三銃士"),
            Map.entry("Lava Hound", "ラヴァハウンド"),
            Map.entry("Ice Spirit", "アイススピリット"),
            Map.entry("Fire Spirit", "ファイアスピリット"),
            Map.entry("Miner", "ディガー"),
            Map.entry("Sparky", "スパーキー"),
            Map.entry("Bowler", "ボウラー"),
            Map.entry("Lumberjack", "ランバージャック"),
            Map.entry("Battle Ram", "攻城バーバリアン"),
            Map.entry("Inferno Dragon", "インフェルノドラゴン"),
            Map.entry("Ice Golem", "アイスゴーレム"),
            Map.entry("Mega Minion", "メガガーゴイル"),
            Map.entry("Dart Goblin", "吹き矢ゴブリン"),
            Map.entry("Goblin Gang", "ゴブリンギャング"),
            Map.entry("Electro Wizard", "エレクトロウィザード"),
            Map.entry("Elite Barbarians", "エリートバーバリアン"),
            Map.entry("Hunter", "ハンター"),
            Map.entry("Executioner", "執行人ファルチェ"),
            Map.entry("Bandit", "アサシン ユーノ"),
            Map.entry("Royal Recruits", "見習い親衛隊"),
            Map.entry("Night Witch", "ダークネクロ"),
            Map.entry("Bats", "コウモリの群れ"),
            Map.entry("Royal Ghost", "ロイヤルゴースト"),
            Map.entry("Ram Rider", "ラムライダー"),
            Map.entry("Zappies", "ザッピー"),
            Map.entry("Rascals", "アウトロー"),
            Map.entry("Cannon Cart", "60式ムート"),
            Map.entry("Mega Knight", "メガナイト"),
            Map.entry("Skeleton Barrel", "スケルトンバレル"),
            Map.entry("Flying Machine", "ホバリング砲"),
            Map.entry("Wall Breakers", "ウォールブレイカー"),
            Map.entry("Royal Hogs", "ロイヤルホグ"),
            Map.entry("Goblin Giant", "ゴブジャイアント"),
            Map.entry("Fisherman", "漁師トリトン"),
            Map.entry("Magic Archer", "マジックアーチャー"),
            Map.entry("Electro Dragon", "ライトニングドラゴン"),
            Map.entry("Firecracker", "ロケット砲士"),
            Map.entry("Mighty Miner", "マイティディガー"),
            Map.entry("Elixir Golem", "エリクサーゴーレム"),
            Map.entry("Battle Healer", "バトルヒーラー"),
            Map.entry("Skeleton King", "スケルトンキング"),
            Map.entry("Archer Queen", "アーチャークイーン"),
            Map.entry("Golden Knight", "ゴールドナイト"),
            Map.entry("Monk", "モンク"),
            Map.entry("Skeleton Dragons", "スケルトンドラゴン"),
            Map.entry("Mother Witch", "マザーネクロマンサー"),
            Map.entry("Electro Spirit", "エレクトロスピリット"),
            Map.entry("Electro Giant", "エレクトロジャイアント"),
            Map.entry("Phoenix", "フェニックス"),
            Map.entry("Little Prince", "リトルプリンス"),
            Map.entry("Goblin Machine", "ゴブリンマシン"),
            Map.entry("Suspicious Bush", "ステルスブッシュ"),
            Map.entry("Goblinstein", "ゴブリンシュタイン"),
            Map.entry("Rune Giant", "鍛冶屋ジャイアント"),
            Map.entry("Minion Giant", "ガーゴイルジャイアント"),
            Map.entry("Berserker", "バーサーカー"),
            Map.entry("Fireball", "ファイアボール"),
            Map.entry("Arrows", "矢の雨"),
            Map.entry("Rage", "レイジ"),
            Map.entry("Rocket", "ロケット"),
            Map.entry("Goblin Barrel", "ゴブリンバレル"),
            Map.entry("Freeze", "フリーズ"),
            Map.entry("Mirror", "鏡"),
            Map.entry("Lightning", "ライトニング"),
            Map.entry("Zap", "ザップ"),
            Map.entry("Poison", "ポイズン"),
            Map.entry("Graveyard", "スケルトンラッシュ"),
            Map.entry("Tornado", "トルネード"),
            Map.entry("Clone", "クローン"),
            Map.entry("Earthquake", "アースクエイク"),
            Map.entry("The Log", "ローリングウッド"),
            Map.entry("Barbarian Barrel", "ローリングバーバリアン"),
            Map.entry("Heal Spirit", "ヒールスピリット"),
            Map.entry("Giant Snowball", "巨大雪玉"),
            Map.entry("Royal Delivery", "ロイヤルデリバリー"),
            Map.entry("Void", "ボイド"),
            Map.entry("Vine", "ヴァイン"),
            Map.entry("Cannon", "大砲"),
            Map.entry("Tesla", "テスラ"),
            Map.entry("Mortar", "迫撃砲"),
            Map.entry("Inferno Tower", "インフェルノタワー"),
            Map.entry("Bomb Tower", "ボムタワー"),
            Map.entry("Barbarian Hut", "バーバリアンの小屋"),
            Map.entry("Tombstone", "墓石"),
            Map.entry("Furnace", "オーブン"),
            Map.entry("Goblin Hut", "ゴブリンの小屋"),
            Map.entry("Elixir Collector", "エリクサーポンプ"),
            Map.entry("X-Bow", "巨大クロスボウ"),
            Map.entry("Goblin Cage", "ゴブリンの檻"),
            Map.entry("Goblin Drill", "ゴブリンドリル"),
            // タワーユニット(プリンセスタワーに配置するカード)。ゲーム内表記に合わせる。
            Map.entry("Tower Princess", "タワープリンセス"),
            Map.entry("Cannoneer", "ブラスター"),
            Map.entry("Dagger Duchess", "ダガーガール"),
            Map.entry("Royal Chef", "ロイヤルシェフ")
    );

    private CardNameLabels() {
    }

    public static String label(String rawName, Locale locale) {
        if (rawName == null || rawName.isBlank()) {
            return "-";
        }
        boolean useJapanese = locale != null && "ja".equalsIgnoreCase(locale.getLanguage());
        if (!useJapanese) {
            return rawName;
        }
        String japanese = JAPANESE_LABELS.get(rawName);
        return japanese == null ? rawName : japanese;
    }
}
