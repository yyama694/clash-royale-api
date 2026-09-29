"""画面に出している公式APIの値が、ラベルどおりの意味かを、独立した別のデータと照合する。

使い方: python semantics.py <作業ディレクトリ>   (collect.py の出力を読む)
"""
import collections
import json
import os
import sys

from common import FRIENDLY_TYPES, both_sides, result, streak


def main(work):
    data = json.load(open(os.path.join(work, "players.json"), encoding="utf-8"))
    ranking = json.load(open(os.path.join(work, "rank_global.json"), encoding="utf-8"))
    if isinstance(ranking, dict):
        ranking = ranking["items"]
    n = len(data)
    print(f"対象 {n}人\n")

    # 1. 連勝・連敗: currentWinLoseStreak と対戦履歴の直近の並び
    mismatch = opposite = big = ranked = ranked_mismatch = 0
    pvp_ok = pvp_ng = 0
    examples = []
    for tag, d in data.items():
        p = d["player"]
        shown = p.get("currentWinLoseStreak") or 0
        log = [b for b in d["battlelog"] if both_sides(b)]
        actual = streak([result(b) for b in log if b["type"] not in FRIENDLY_TYPES])
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
    print("[連勝・連敗] currentWinLoseStreak と、対戦履歴(フレンドバトル除く)の直近の連勝・連敗")
    print(f"  不一致 {mismatch}/{n}人、向きが逆 {opposite}人、20連勝以上の表示 {big}人、"
          f"ランク戦の順位がある人 {ranked}人中 不一致 {ranked_mismatch}人")
    print(f"  通常のトロフィー戦(type=PvP)だけで数えると一致: {pvp_ok}/{pvp_ok + pvp_ng}人")
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
          f"うちシーズン・トロフィーロードの値がある人 {len(capped)}人")
    for name, t, s, b in capped[:3]:
        print(f"    例: {name} 表示={t} シーズン={s} (最高 {b})")

    # 3. 勝敗判定: クラウン数による判定とトロフィー増減の符号
    seen, checked, wrong = set(), 0, 0
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
            wrong += (r == "W" and change <= 0) or (r == "L" and change >= 0) or r == "D"
    print(f"\n[勝敗判定] トロフィー増減のある {checked}戦で、クラウン数の判定と食い違い {wrong}戦")

    # 4. ランク戦の順位・レーティング: プレイヤー情報とランキング
    diff = 0
    compared = 0
    for r in ranking[:40]:
        p = data.get(r["tag"], {}).get("player")
        if not p:
            continue
        compared += 1
        cur = p.get("currentPathOfLegendSeasonResult") or {}
        diff += (cur.get("rank"), cur.get("trophies")) != (r["rank"], r["eloRating"])
    print(f"\n[ランク戦] 上位 {compared}人で、プレイヤー情報の順位・レーティングとランキングの食い違い {diff}人"
          "(取得の間に対戦した人はずれうる)")
    lower = [(d["player"]["name"], d["player"]["currentPathOfLegendSeasonResult"], d["player"]["bestPathOfLegendSeasonResult"])
             for d in data.values()
             if (d["player"].get("currentPathOfLegendSeasonResult") or {}).get("rank")
             and (d["player"].get("bestPathOfLegendSeasonResult") or {}).get("rank")
             and d["player"]["currentPathOfLegendSeasonResult"]["rank"] < d["player"]["bestPathOfLegendSeasonResult"]["rank"]]
    print(f"  今シーズンの順位が「最高シーズン」より上の人 {len(lower)}人(最高シーズンは終了済みのシーズンだけ)")

    # 5. 対戦種別の内訳(モード名の表示を確かめる材料)
    modes = collections.Counter((b["type"], b.get("gameMode", {}).get("name")) for d in data.values() for b in d["battlelog"])
    print("\n[対戦種別 type / gameMode.name の上位]")
    for (t, m), c in modes.most_common(15):
        print(f"  {c:5d} {t} / {m}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
