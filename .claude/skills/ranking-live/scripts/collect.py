#!/usr/bin/env python3
"""個人ランキング(ランク戦)上位を取得し、前回との差分から「実況のネタ」を出す。

実況は「今の順位」だけでは書けない。前回からどう動いたか(首位交代・連続首位・
浮上/陥落)が分かって初めてニュースになるので、取得のたびに history/ に残して
次回との比較に使う。文面そのものは書かない。どの切り口を主役にするかは
データを見て人(モデル)が決める領域なので、ここでは判断材料だけを並べる。
"""

import argparse
import json
import re
import sys
import urllib.request
from datetime import datetime, timezone, timedelta
from pathlib import Path

BASE = "https://princess-tower.duckdns.org"
JST = timezone(timedelta(hours=9))

# 1プレイヤー分の<tr>。lang指定なしの既定(英語)で取るため、ラベルの言語に依存しない
# 属性・クラス名だけを手がかりにする。
ROW = re.compile(
    r'<span class="rank">(?P<rank>\d+)</span>.*?'
    r'href="/player/(?P<tag>[^"]+)"><bdi>(?P<name>.*?)</bdi>.*?'
    r'class="num" data-label="[^"]*">(?P<rating>[\d,]+)<',
    re.DOTALL,
)
# クラン名は任意(未所属がいる)。行ごとに別途拾う。
CLAN = re.compile(r'href="/clan/[^"]+">\s*<bdi>(?P<clan>.*?)</bdi>', re.DOTALL)


def fetch(country=None, timeout=30):
    url = f"{BASE}/ranking/players/table"
    if country:
        url += f"?country={country}"
    req = urllib.request.Request(url, headers={"User-Agent": "ranking-live/1.0"})
    with urllib.request.urlopen(req, timeout=timeout) as res:
        return res.read().decode("utf-8")


def parse(html, limit):
    players = []
    # 行単位に割ってから拾う。クラン名を「その行のもの」に確実に紐づけるため。
    for chunk in html.split("<tr>")[1:]:
        m = ROW.search(chunk)
        if not m:
            continue
        clan = CLAN.search(chunk)
        players.append({
            "rank": int(m.group("rank")),
            "tag": m.group("tag"),
            "name": m.group("name"),
            "rating": int(m.group("rating").replace(",", "")),
            "clan": clan.group("clan") if clan else None,
        })
        if len(players) >= limit:
            break
    return players


def history_path(skill_dir):
    return Path(skill_dir) / "history" / "players.tsv"


def load_history(path):
    """日時ごとのスナップショットを新しい順に返す。"""
    if not path.exists():
        return []
    snapshots = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) < 5:
            continue
        stamp, rank, tag, name, rating = parts[:5]
        snapshots.setdefault(stamp, []).append({
            "rank": int(rank), "tag": tag, "name": name, "rating": int(rating),
        })
    ordered = sorted(snapshots.items(), key=lambda kv: kv[0], reverse=True)
    return [{"stamp": s, "players": sorted(p, key=lambda x: x["rank"])} for s, p in ordered]


def append_history(path, stamp, players):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8", newline="\n") as f:
        for p in players:
            f.write(f"{stamp}\t{p['rank']}\t{p['tag']}\t{p['name']}\t{p['rating']}\n")


def leader_streak(history, current_leader_tag):
    """首位が何回連続で同じかを、暦日ごとに数える。

    1日に何度も実行され得るので、同じ日の複数スナップショットは1日として数える
    (「3日連続首位」と書きたいのであって「3回連続」ではないため)。
    """
    seen_days = []
    for snap in history:
        day = snap["stamp"][:10]
        if seen_days and seen_days[-1][0] == day:
            continue
        top = next((p for p in snap["players"] if p["rank"] == 1), None)
        seen_days.append((day, top["tag"] if top else None))
    days = 1  # 今日の分
    today = datetime.now(JST).strftime("%Y-%m-%d")
    for day, tag in seen_days:
        if day == today:
            continue
        if tag != current_leader_tag:
            break
        days += 1
    return days


def diff(current, previous):
    """前回スナップショットとの比較。実況の切り口はここから選ぶ。"""
    if not previous:
        return {"first_run": True}
    prev_by_tag = {p["tag"]: p for p in previous["players"]}
    cur_by_tag = {p["tag"]: p for p in current}
    prev_leader = next((p for p in previous["players"] if p["rank"] == 1), None)
    leader = current[0]

    movers = []
    for p in current:
        old = prev_by_tag.get(p["tag"])
        if old:
            movers.append({
                "name": p["name"], "tag": p["tag"],
                "rank": p["rank"], "rank_change": old["rank"] - p["rank"],
                "rating": p["rating"], "rating_change": p["rating"] - old["rating"],
            })
    climbers = sorted((m for m in movers if m["rank_change"] > 0),
                      key=lambda m: -m["rank_change"])
    fallers = sorted((m for m in movers if m["rank_change"] < 0),
                     key=lambda m: m["rank_change"])

    return {
        "first_run": False,
        "since": previous["stamp"],
        "leader_changed": bool(prev_leader and prev_leader["tag"] != leader["tag"]),
        "previous_leader": prev_leader,
        "leader_rating_change": leader["rating"] - prev_by_tag[leader["tag"]]["rating"]
                                if leader["tag"] in prev_by_tag else None,
        "entered": [{"name": p["name"], "rank": p["rank"]}
                    for p in current if p["tag"] not in prev_by_tag],
        "left": [{"name": p["name"], "rank": p["rank"]}
                 for p in previous["players"] if p["tag"] not in cur_by_tag],
        "climbers": climbers[:3],
        "fallers": fallers[:3],
    }


def main():
    # Windowsの既定の標準出力はCP932で、プレイヤー名の絵文字(✨など)が出せず落ちる。
    for stream in (sys.stdout, sys.stderr):
        stream.reconfigure(encoding="utf-8")

    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=10, help="取得・記録する人数")
    ap.add_argument("--country", help="国コード(省略時はグローバル)")
    ap.add_argument("--no-record", action="store_true",
                    help="history に追記しない(下見だけしたいとき)")
    args = ap.parse_args()

    skill_dir = Path(__file__).resolve().parent.parent
    players = parse(fetch(args.country), args.limit)
    if not players:
        print("ランキングを取得できませんでした(サイトの応答を確認してください)",
              file=sys.stderr)
        return 1

    path = history_path(skill_dir)
    history = load_history(path)
    stamp = datetime.now(JST).strftime("%Y-%m-%d %H:%M")

    result = {
        "fetched_at_jst": stamp,
        "scope": args.country or "global",
        "players": players,
        "gap_1_2": players[0]["rating"] - players[1]["rating"] if len(players) > 1 else None,
        "spread_1_last": players[0]["rating"] - players[-1]["rating"] if len(players) > 1 else None,
        "leader_streak_days": leader_streak(history, players[0]["tag"]),
        "diff": diff(players, history[0] if history else None),
        "history_runs": len(history),
    }

    if not args.no_record and args.country is None:
        # 記録はグローバルだけにする。国別まで混ぜると首位の連続日数が狂うため。
        append_history(path, stamp, players)

    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
