"""照合用のサンプルを公式APIから集める。

使い方: python collect.py <作業ディレクトリ>
上位勢だけだと偏るので、ランク戦の世界上位・日本上位・クラン経由の一般層を混ぜる(約100〜120人、数分)。
出力: <作業ディレクトリ>/players.json({tag: {player, battlelog}})、rank_global.json(ランク戦の世界上位200人)
"""
import json
import os
import sys
import time

from common import api, enc

JAPAN = 57000122


def members_of(clan_search, per_clan=8, clans=4):
    tags = []
    for c in (api(clan_search) or {}).get("items", [])[:clans]:
        detail = api("/clans/" + enc(c["tag"])) or {}
        tags += [m["tag"] for m in detail.get("memberList", [])[:per_clan]]
    return tags


def main(work):
    os.makedirs(work, exist_ok=True)
    ranking = api("/locations/global/pathoflegend/players?limit=200")["items"]
    json.dump(ranking, open(os.path.join(work, "rank_global.json"), "w", encoding="utf-8"), ensure_ascii=False)
    tags = [p["tag"] for p in ranking[:40]]
    tags += [p["tag"] for p in (api(f"/locations/{JAPAN}/pathoflegend/players?limit=15") or {}).get("items", [])]
    tags += members_of("/clans?name=japan&limit=10&minScore=30000")
    tags += members_of("/clans?name=royale&limit=10&minMembers=20")
    seen = set()
    tags = [t for t in tags if not (t in seen or seen.add(t))]

    data = {}
    for t in tags:
        player = api("/players/" + enc(t))
        if player is None:
            continue
        data[t] = {"player": player, "battlelog": api("/players/" + enc(t) + "/battlelog") or []}
        time.sleep(0.2)
    json.dump(data, open(os.path.join(work, "players.json"), "w", encoding="utf-8"), ensure_ascii=False)
    print(f"{len(data)}人分を {os.path.join(work, 'players.json')} に保存した")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
