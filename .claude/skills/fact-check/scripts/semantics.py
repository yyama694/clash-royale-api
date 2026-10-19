"""画面に出している公式APIの値が、ラベルどおりの意味かを、独立した別のデータと照合する。

使い方: python semantics.py <作業ディレクトリ>   (collect.py の出力を読む)
"""
import collections
import datetime
import json
import os
import sys

from common import both_sides, card_performance, counted, crowns, is_duel, result, score, streak, win_rate


def api_time(s):
    return datetime.datetime.strptime(s, "%Y%m%dT%H%M%S.%fZ").replace(tzinfo=datetime.timezone.utc)


def current_season_start(now=None):
    """SeasonCalendar と同じ決まり: 毎月第1月曜の 09:00 UTC。"""
    now = now or datetime.datetime.now(datetime.timezone.utc)
    year, month = now.year, now.month
    for _ in range(2):
        first = datetime.datetime(year, month, 1, 9, tzinfo=datetime.timezone.utc)
        start = first + datetime.timedelta(days=(7 - first.weekday()) % 7)
        if start <= now:
            return start
        year, month = (year, month - 1) if month > 1 else (year - 1, 12)
    return start


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

    # 7. 苦手カード: 2026-10-01から本人の全体の勝率より低いカードだけを選ぶ。それ以前は相対順位だけで選んでいて、
    #    勝率の高い人の「苦手」に勝率80%のカードが並んでいた(9/30、123人中23人)
    shown_weak = high_weak = above_overall = 0
    for d in data.values():
        _, weak = card_performance(d["battlelog"])
        battles = counted(d["battlelog"])
        overall = sum(result(b) == "W" for b in battles) / len(battles) if battles else 0
        if weak:
            shown_weak += 1
            high_weak += min(win_rate(w, u) for _, w, u in weak) >= 50
            above_overall += any(w / u >= overall for _, w, u in weak)
    print(f"\n[苦手カード] 苦手が出る {shown_weak}人中、全体の勝率以上のカードが苦手に入っている人 {above_overall}人"
          f"(0人のはず)。参考: 苦手がすべて勝率50%以上の人 {high_weak}人(勝率の高い人では正しくありうる)")

    # 8. 対戦ごとの増減(2026-10-04から対戦履歴に出す): trophyChange が本当にその対戦での増減かを、
    #    次の同じ種類の対戦の startingTrophies(と今のレーティング)と突き合わせる。
    season_start = current_season_start()
    pvp, celestial, latest_rating = [0, 0], [0, 0], [0, 0]
    lower_league, trail_ladder = collections.Counter(), collections.Counter()
    for d in data.values():
        me = d["player"]["tag"]
        mine = [(b, b["team"][0]) for b in d["battlelog"]
                if both_sides(b) and len(b["team"]) == 1 and b["team"][0].get("tag") == me]
        for kind, counter in (("PvP", pvp), ("celestial", celestial)):
            seq = [(b, t) for b, t in mine
                   if (kind == "PvP" and b["type"] == "PvP")
                   or (kind == "celestial" and b["type"] == "pathOfLegend" and b.get("leagueNumber") == 7
                       and api_time(b["battleTime"]) >= season_start)]
            for (_, newer), (_, older) in zip(seq, seq[1:]):
                if older.get("startingTrophies") is None or newer.get("startingTrophies") is None:
                    continue
                counter[0 if older["startingTrophies"] + (older.get("trophyChange") or 0) == newer["startingTrophies"] else 1] += 1
            if kind == "celestial" and seq and seq[0][1].get("startingTrophies") is not None:
                rating = (d["player"].get("currentPathOfLegendSeasonResult") or {}).get("trophies")
                latest_rating[0 if seq[0][1]["startingTrophies"] + (seq[0][1].get("trophyChange") or 0) == rating else 1] += 1
        for b, t in mine:
            if b["type"] == "pathOfLegend" and b.get("leagueNumber") != 7:
                lower_league[(result(b), t.get("trophyChange"))] += 1
            if b["type"] == "trail" and (b.get("gameMode") or {}).get("name") == "Ladder":
                trail_ladder[(t.get("startingTrophies"), "増減あり" if t.get("trophyChange") else "増減なし")] += 1
    print("\n[対戦ごとの増減] 古い方の startingTrophies + trophyChange が、次の同じ種類の対戦の startingTrophies と一致するか")
    print(f"  トロフィー戦(PvP): 一致 {pvp[0]} / 不一致 {pvp[1]}組")
    print(f"  天界のランク戦(今シーズン): 一致 {celestial[0]} / 不一致 {celestial[1]}組。"
          f"最新の対戦の後の値が今のレーティングと一致 {latest_rating[0]} / 不一致 {latest_rating[1]}人")
    print(f"  天界より下のランク戦の(勝敗, trophyChange)の上位(画面には出さない。1.158): {lower_league.most_common(4)}")
    print(f"  14,000到達後のトロフィー戦(trail/Ladder)の(startingTrophies, 増減): {trail_ladder.most_common(4)}"
          "(画面には出していない。TODOの「サイトの情報のファクトチェック」)")

    # 9. ランク戦のリーグ(2026-10-14から対戦履歴に出す): 世界の順位がある人(天界でしか付かない)の
    #    今シーズンのランク戦が7(天界)か、プレイヤーの今シーズンのリーグと最新のランク戦のリーグが同じか。
    ranked_leagues, same_league = collections.Counter(), [0, 0]
    for d in data.values():
        cur = d["player"].get("currentPathOfLegendSeasonResult") or {}
        ranked = [b for b in d["battlelog"] if b["type"] == "pathOfLegend" and api_time(b["battleTime"]) >= season_start]
        if cur.get("rank") and cur.get("trophies"):
            ranked_leagues.update(b.get("leagueNumber") for b in ranked)
        if ranked and cur.get("leagueNumber"):
            same_league[0 if ranked[0].get("leagueNumber") == cur["leagueNumber"] else 1] += 1
    print(f"\n[ランク戦のリーグ] 世界の順位がある人の今シーズンのランク戦の leagueNumber: {dict(ranked_leagues)}(7=天界のはず)")
    print(f"  最新のランク戦のリーグと、プレイヤーの今シーズンのリーグ: 一致 {same_league[0]} / 不一致 {same_league[1]}人")

    # 10. クラン対戦のデュエル(2本先取。1.180から勝敗はラウンドの勝ち数): 勝った側がちょうど2ラウンド取っているか。
    duels = {}
    for d in data.values():
        for b in d["battlelog"]:
            if both_sides(b) and is_duel(b):
                duels[(b["battleTime"], b["team"][0].get("tag"))] = b
    not_two = sum(max(score(b)) != 2 for b in duels.values())
    by_crowns = sum((crowns(b["team"]) > crowns(b["opponent"])) != (result(b) == "W") for b in duels.values())
    print(f"\n[デュエル] {len(duels)}戦で、勝った側の取ったラウンドが2でない対戦 {not_two}戦(0のはず)。"
          f"参考: クラウンの合計で判定すると勝敗が変わる対戦 {by_crowns}戦")

    # 11. 対戦種別の内訳(モード名の表示を確かめる材料)
    modes = collections.Counter((b["type"], b.get("gameMode", {}).get("name")) for d in data.values() for b in d["battlelog"])
    print("\n[対戦種別 type / gameMode.name の上位]")
    for (t, m), c in modes.most_common(15):
        print(f"  {c:5d} {t} / {m}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
