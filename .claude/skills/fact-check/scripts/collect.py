"""照合用のサンプルを公式APIから集める。

使い方: python collect.py <作業ディレクトリ>
上位勢だけだと偏るので、ランク戦の世界上位・日本上位・クラン経由・育成途中の層を混ぜる(約140人、数分)。
出力: <作業ディレクトリ>/players.json({tag: {tier, player, battlelog}})、rank_global.json(ランク戦の世界上位200人)、
      cards.json(/cards の全カード)
"""
import json
import os
import sys
import time

from common import api, enc

JAPAN = 57000122
# クラン名の検索は強いクランが上に来るので、育成途中の層はクランスコアの低いクランから拾う。
LOW_TIER_CLAN_NAMES = ("clash", "team", "family", "amigos", "noob", "new")


def members_of(clan_search, per_clan=8, clans=4):
    tags = []
    for c in (api(clan_search) or {}).get("items", [])[:clans]:
        detail = api("/clans/" + enc(c["tag"])) or {}
        tags += [m["tag"] for m in detail.get("memberList", [])[:per_clan]]
    return tags


def low_tier_members(clans=6):
    """クランスコアの低いクランから、トロフィーの低い3人と中央の1人を取る(9/30の実施で31〜2,891トロフィー)。"""
    found = {}
    for name in LOW_TIER_CLAN_NAMES:
        for c in (api(f"/clans?name={name}&limit=100&minMembers=10") or {}).get("items", []):
            found[c["tag"]] = c
    tags = []
    for c in sorted(found.values(), key=lambda c: c["clanScore"])[:clans]:
        members = sorted((api("/clans/" + enc(c["tag"])) or {}).get("memberList", []), key=lambda m: m["trophies"])
        if members:
            tags += [m["tag"] for m in members[:3]] + [members[len(members) // 2]["tag"]]
    return tags


def main(work):
    os.makedirs(work, exist_ok=True)
    ranking = api("/locations/global/pathoflegend/players?limit=200")["items"]
    json.dump(ranking, open(os.path.join(work, "rank_global.json"), "w", encoding="utf-8"), ensure_ascii=False)
    json.dump(api("/cards"), open(os.path.join(work, "cards.json"), "w", encoding="utf-8"), ensure_ascii=False)
    tiers = [
        ("世界上位", [p["tag"] for p in ranking[:40]]),
        ("日本上位", [p["tag"] for p in (api(f"/locations/{JAPAN}/pathoflegend/players?limit=15") or {}).get("items", [])]),
        ("クラン経由", members_of("/clans?name=japan&limit=10&minScore=30000")
         + members_of("/clans?name=royale&limit=10&minMembers=20")),
        ("育成途中", low_tier_members()),
    ]

    data = {}
    for tier, tags in tiers:
        for t in tags:
            if t in data:
                continue
            player = api("/players/" + enc(t))
            if player is None:
                continue
            data[t] = {"tier": tier, "player": player, "battlelog": api("/players/" + enc(t) + "/battlelog") or []}
            time.sleep(0.2)
    json.dump(data, open(os.path.join(work, "players.json"), "w", encoding="utf-8"), ensure_ascii=False)
    counts = {tier: sum(d["tier"] == tier for d in data.values()) for tier, _ in tiers}
    print(f"{len(data)}人分({counts})を {os.path.join(work, 'players.json')} に保存した")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
