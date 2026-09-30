"""画面に出している公式APIの値が、ラベルどおりの意味かを、独立した別のデータと照合する。

使い方: python semantics.py <作業ディレクトリ>   (collect.py の出力を読む)
"""
import collections
import json
import os
import sys

from common import both_sides, card_performance, counted, result, streak, win_rate


def by_tier(data, predicate):
    """層ごとの「該当人数/人数」。"""
    counts = collections.defaultdict(lambda: [0, 0])
    for d in data.values():
        c = counts[d.get("tier", "-")]
        c[0] += bool(predicate(d))
        c[1] += 1
    return "、".join(f"{tier} {hit}/{n}" for tier, (hit, n) in counts.items())


def main(work):
    data = json.load(open(os.path.join(work, "players.json"), encoding="utf-8"))
    ranking = json.load(open(os.path.join(work, "rank_global.json"), encoding="utf-8"))
    if isinstance(ranking, dict):
        ranking = ranking["items"]
    cards_path = os.path.join(work, "cards.json")
    all_cards = json.load(open(cards_path, encoding="utf-8"))["items"] if os.path.exists(cards_path) else None
    n = len(data)
    print(f"対象 {n}人\n")

    # 1. 連勝・連敗(参考): 画面は2026-10-01から対戦履歴から数えていて(転記は compare_player.py で確かめる)、
    #    currentWinLoseStreak は使っていない。APIの値の意味が変わっていないかの記録として残す。
    mismatch = opposite = big = ranked = ranked_mismatch = 0
    pvp_ok = pvp_ng = 0
    examples = []
    actual_of = {}
    for tag, d in data.items():
        p = d["player"]
        shown = p.get("currentWinLoseStreak") or 0
        log = [b for b in d["battlelog"] if both_sides(b)]
        actual = streak([result(b) for b in counted(log)])
        actual_of[tag] = actual
        if shown != actual:
            mismatch += 1
            if abs(shown) >= 20 or shown * actual < 0:
                examples.append((abs(shown), tag, p["name"], shown, actual))
        opposite += shown * actual < 0
        big += shown >= 20
        if (p.get("currentPathOfLegendSeasonResult") or {}).get("rank"):
            ranked += 1
            ranked_mismatch += shown != actual
        pvp = [result(b) for b in log if b["type"] == "PvP"]
        if pvp:
            s = streak(pvp)
            closed = len(pvp) > abs(s)
            good = s == shown if closed else (shown * s > 0 and abs(shown) >= abs(s))
            pvp_ok += good
            pvp_ng += not good
    print("[連勝・連敗(参考。画面では使っていない)] currentWinLoseStreak と、"
          "対戦履歴(フレンドバトル・船の防衛を除く)の直近の連勝・連敗")
    print(f"  不一致 {mismatch}/{n}人、向きが逆 {opposite}人、20連勝以上の表示 {big}人、"
          f"ランク戦の順位がある人 {ranked}人中 不一致 {ranked_mismatch}人")
    print("  層ごとの不一致: " + by_tier(data, lambda d: (d["player"].get("currentWinLoseStreak") or 0)
                                         != actual_of[d["player"]["tag"]]))
    print(f"  通常のトロフィー戦(type=PvP)だけで数えると一致: {pvp_ok}/{pvp_ok + pvp_ng}人")
    empty = [d for d in data.values() if not d["battlelog"]]
    print(f"  対戦履歴が空の人 {len(empty)}人。うち連勝・連敗が0でない人 "
          f"{sum(bool(d['player'].get('currentWinLoseStreak')) for d in empty)}人")
    for _, tag, name, shown, actual in sorted(examples, reverse=True)[:5]:
        print(f"    例: {name}({tag}) 表示={shown} 対戦履歴={actual}")

    # 2. トロフィー: 14,000で止まる trophies と、シーズン・トロフィーロードの値
    capped = []
    for tag, d in data.items():
        p = d["player"]
        seasonal = [v for k, v in (p.get("progress") or {}).items() if k.startswith("seasonal-trophy-road")]
        if p["trophies"] >= 14000 and seasonal and seasonal[0].get("bestTrophies"):
            capped.append((p["name"], p["trophies"], seasonal[0]["trophies"], seasonal[0]["bestTrophies"]))
    print(f"\n[トロフィー] trophies>=14000 の人 {sum(d['player']['trophies'] >= 14000 for d in data.values())}人。"
          f"うちシーズン・トロフィーロードの値がある人 {len(capped)}人、そのうち15,000ちょうど "
          f"{sum(s == 15000 for _, _, s, _ in capped)}人(ゲーム内でも15,000が上限。9/30に確認)")
    for name, t, s, b in capped[:3]:
        print(f"    例: {name} 表示={t} シーズン={s} (最高 {b})")

    # 3. 勝敗判定: クラウン数による判定とトロフィー増減の符号
    # ランク戦の引き分けは両者ともレーティングが下がるので、引き分けは食い違いに数えず別に出す(9/30に確認)。
    seen, checked, wrong, draws_moved = set(), 0, 0, 0
    for d in data.values():
        for b in d["battlelog"]:
            if not both_sides(b):
                continue
            key = (b["battleTime"], b["team"][0].get("tag"))
            change = b["team"][0].get("trophyChange")
            if key in seen or change is None:
                continue
            seen.add(key)
            checked += 1
            r = result(b)
            wrong += (r == "W" and change <= 0) or (r == "L" and change >= 0)
            draws_moved += r == "D" and change != 0
    print(f"\n[勝敗判定] トロフィー増減のある {checked}戦で、クラウン数の判定と食い違い {wrong}戦"
          f"(ほかに、引き分けでトロフィー・レーティングが動いた対戦 {draws_moved}戦)")

    # 4. ランク戦の順位・レーティング: プレイヤー情報とランキング
    rating_diff = rank_only = compared = 0
    for r in ranking[:40]:
        p = data.get(r["tag"], {}).get("player")
        if not p:
            continue
        compared += 1
        cur = p.get("currentPathOfLegendSeasonResult") or {}
        rating_diff += cur.get("trophies") != r["eloRating"]
        rank_only += cur.get("trophies") == r["eloRating"] and cur.get("rank") != r["rank"]
    # 取得の合間にほかの人が対戦すると、レーティングが同じまま順位だけが押し出される(9/30に10人)
    print(f"\n[ランク戦] 上位 {compared}人で、プレイヤー情報とランキングのレーティングの食い違い {rating_diff}人"
          f"(本人が取得の間に対戦するとずれうる)、レーティングは同じで順位だけのずれ {rank_only}人")
    lower = [(d["player"]["name"], d["player"]["currentPathOfLegendSeasonResult"], d["player"]["bestPathOfLegendSeasonResult"])
             for d in data.values()
             if (d["player"].get("currentPathOfLegendSeasonResult") or {}).get("rank")
             and (d["player"].get("bestPathOfLegendSeasonResult") or {}).get("rank")
             and d["player"]["currentPathOfLegendSeasonResult"]["rank"] < d["player"]["bestPathOfLegendSeasonResult"]["rank"]]
    print(f"  今シーズンの順位が「最高シーズン」より上の人 {len(lower)}人(最高シーズンは終了済みのシーズンだけ)")

    # 5. キングタワーレベル: ゲーム内の最小は1のはずだが、APIが0を返す人がいる
    zero = [d["player"] for d in data.values() if not d["player"].get("kingTowerLevel")]
    print(f"\n[キングタワーレベル] 0(または無し)の人 {len(zero)}人(層ごと: "
          + by_tier(data, lambda d: not d["player"].get("kingTowerLevel")) + ")")
    for p in zero[:3]:
        print(f"    例: {p['name']}({p['tag']}) collectionLevel={p.get('collectionLevel')}")

    # 6. カードコレクション: cards は所持しているカードだけを含む
    if all_cards:
        owned = [len(d["player"].get("cards") or []) for d in data.values()]
        fewer = [c for c in owned if c < len(all_cards)]
        print(f"\n[カードコレクション] 全カード {len(all_cards)}枚。cards がそれより少ない人 {len(fewer)}/{n}人"
              + (f"(最少 {min(fewer)}枚)" if fewer else ""))

    # 7. 苦手カード: 相対順位なので、勝率が高くても「苦手」に並ぶ
    shown_weak = high_weak = 0
    for d in data.values():
        _, weak = card_performance(d["battlelog"])
        if weak:
            shown_weak += 1
            high_weak += min(win_rate(w, u) for _, w, u in weak) >= 50
    print(f"\n[苦手カード] 得意・苦手が出る {shown_weak}人中、苦手カードがすべて勝率50%以上の人 {high_weak}人")

    # 8. 対戦種別の内訳(モード名の表示を確かめる材料)
    modes = collections.Counter((b["type"], b.get("gameMode", {}).get("name")) for d in data.values() for b in d["battlelog"])
    print("\n[対戦種別 type / gameMode.name の上位]")
    for (t, m), c in modes.most_common(15):
        print(f"  {c:5d} {t} / {m}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
