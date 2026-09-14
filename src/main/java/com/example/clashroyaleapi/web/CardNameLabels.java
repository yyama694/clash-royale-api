package com.example.clashroyaleapi.web;

import java.util.Map;

/**
 * Clash Royale公式APIが返すカード名(英語)を、アクセス言語が日本語の場合のみ日本語名に変換する。
 * 未知のカード名は英語名のままフォールバックする。
 */
public final class CardNameLabels {

    private static final Map<String, String> JAPANESE_LABELS = Map.ofEntries(
            Map.entry("Knight", "ナイト"),
            Map.entry("Archers", "アーチャー"),
            Map.entry("Goblins", "ゴブリン"),
            Map.entry("Giant", "ジャイアント"),
            Map.entry("P.E.K.K.A", "P.E.K.K.A"),
            Map.entry("Minions", "ミニオン"),
            Map.entry("Balloon", "バルーン"),
            Map.entry("Witch", "魔女"),
            Map.entry("Barbarians", "バーバリアン"),
            Map.entry("Golem", "ゴーレム"),
            Map.entry("Skeletons", "スケルトン"),
            Map.entry("Valkyrie", "ヴァルキリー"),
            Map.entry("Skeleton Army", "スケルトンアーミー"),
            Map.entry("Bomber", "ボンバー"),
            Map.entry("Musketeer", "マスケット銃士"),
            Map.entry("Baby Dragon", "ベビードラゴン"),
            Map.entry("Prince", "プリンス"),
            Map.entry("Wizard", "ウィザード"),
            Map.entry("Mini P.E.K.K.A", "ミニP.E.K.K.A"),
            Map.entry("Spear Goblins", "スピアゴブリン"),
            Map.entry("Giant Skeleton", "ジャイアントスケルトン"),
            Map.entry("Hog Rider", "ホグライダー"),
            Map.entry("Minion Horde", "ミニオンホード"),
            Map.entry("Ice Wizard", "アイスウィザード"),
            Map.entry("Royal Giant", "ロイヤルジャイアント"),
            Map.entry("Guards", "ガード"),
            Map.entry("Princess", "プリンセス"),
            Map.entry("Dark Prince", "ダークプリンス"),
            Map.entry("Three Musketeers", "3人のマスケット銃士"),
            Map.entry("Lava Hound", "ラヴァハウンド"),
            Map.entry("Ice Spirit", "アイススピリット"),
            Map.entry("Fire Spirit", "ファイアスピリット"),
            Map.entry("Miner", "マイナー"),
            Map.entry("Sparky", "スパーキー"),
            Map.entry("Bowler", "ボウラー"),
            Map.entry("Lumberjack", "ランバージャック"),
            Map.entry("Battle Ram", "バトルラム"),
            Map.entry("Inferno Dragon", "インフェルノドラゴン"),
            Map.entry("Ice Golem", "アイスゴーレム"),
            Map.entry("Mega Minion", "メガミニオン"),
            Map.entry("Dart Goblin", "ダーツゴブリン"),
            Map.entry("Goblin Gang", "ゴブリンギャング"),
            Map.entry("Electro Wizard", "エレクトロウィザード"),
            Map.entry("Elite Barbarians", "エリートバーバリアン"),
            Map.entry("Hunter", "ハンター"),
            Map.entry("Executioner", "エグゼキューショナー"),
            Map.entry("Bandit", "バンディット"),
            Map.entry("Royal Recruits", "ロイヤルリクルート"),
            Map.entry("Night Witch", "ナイトウィッチ"),
            Map.entry("Bats", "コウモリ"),
            Map.entry("Royal Ghost", "ロイヤルゴースト"),
            Map.entry("Ram Rider", "ラムライダー"),
            Map.entry("Zappies", "ザッピーズ"),
            Map.entry("Rascals", "ラスカルズ"),
            Map.entry("Cannon Cart", "キャノンカート"),
            Map.entry("Mega Knight", "メガナイト"),
            Map.entry("Skeleton Barrel", "スケルトンバレル"),
            Map.entry("Flying Machine", "フライングマシーン"),
            Map.entry("Wall Breakers", "ウォールブレイカー"),
            Map.entry("Royal Hogs", "ロイヤルホグ"),
            Map.entry("Goblin Giant", "ゴブリンジャイアント"),
            Map.entry("Fisherman", "フィッシャーマン"),
            Map.entry("Magic Archer", "マジックアーチャー"),
            Map.entry("Electro Dragon", "エレクトロドラゴン"),
            Map.entry("Firecracker", "ファイヤークラッカー"),
            Map.entry("Mighty Miner", "マイティマイナー"),
            Map.entry("Elixir Golem", "エリクサーゴーレム"),
            Map.entry("Battle Healer", "バトルヒーラー"),
            Map.entry("Skeleton King", "スケルトンキング"),
            Map.entry("Archer Queen", "アーチャークイーン"),
            Map.entry("Golden Knight", "ゴールデンナイト"),
            Map.entry("Monk", "モンク"),
            Map.entry("Skeleton Dragons", "スケルトンドラゴン"),
            Map.entry("Mother Witch", "マザーウィッチ"),
            Map.entry("Electro Spirit", "エレクトロスピリット"),
            Map.entry("Electro Giant", "エレクトロジャイアント"),
            Map.entry("Phoenix", "フェニックス"),
            Map.entry("Little Prince", "リトルプリンス"),
            Map.entry("Goblin Machine", "ゴブリンマシーン"),
            Map.entry("Suspicious Bush", "あやしいしげみ"),
            Map.entry("Goblinstein", "ゴブリンシュタイン"),
            Map.entry("Fireball", "ファイアボール"),
            Map.entry("Arrows", "アロー"),
            Map.entry("Rage", "レイジ"),
            Map.entry("Rocket", "ロケット"),
            Map.entry("Goblin Barrel", "ゴブリンバレル"),
            Map.entry("Freeze", "フリーズ"),
            Map.entry("Mirror", "ミラー"),
            Map.entry("Lightning", "ライトニング"),
            Map.entry("Zap", "ザップ"),
            Map.entry("Poison", "ポイズン"),
            Map.entry("Graveyard", "グレイヤード"),
            Map.entry("Tornado", "トルネード"),
            Map.entry("Clone", "クローン"),
            Map.entry("Earthquake", "アースクエイク"),
            Map.entry("Barbarian Barrel", "バーバリアンバレル"),
            Map.entry("Heal Spirit", "ヒールスピリット"),
            Map.entry("Giant Snowball", "ジャイアントスノーボール"),
            Map.entry("Royal Delivery", "ロイヤルデリバリー"),
            Map.entry("Void", "ヴォイド"),
            Map.entry("Cannon", "キャノン"),
            Map.entry("Tesla", "テスラ"),
            Map.entry("Mortar", "迫撃砲"),
            Map.entry("Inferno Tower", "インフェルノタワー"),
            Map.entry("Bomb Tower", "ボムタワー"),
            Map.entry("Barbarian Hut", "バーバリアンハット"),
            Map.entry("Tombstone", "トゥームストーン"),
            Map.entry("Furnace", "ファーネス"),
            Map.entry("Goblin Hut", "ゴブリンハット"),
            Map.entry("Elixir Collector", "エリクサーコレクター"),
            Map.entry("X-Bow", "X-ボウ"),
            Map.entry("Goblin Cage", "ゴブリンケージ"),
            Map.entry("Goblin Drill", "ゴブリンドリル")
    );

    private CardNameLabels() {
    }

    public static String label(String rawName, boolean useJapanese) {
        if (rawName == null || rawName.isBlank()) {
            return "-";
        }
        if (!useJapanese) {
            return rawName;
        }
        String japanese = JAPANESE_LABELS.get(rawName);
        return japanese == null ? rawName : japanese;
    }
}
