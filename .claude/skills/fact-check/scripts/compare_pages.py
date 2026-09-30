"""プレイヤー情報画面以外の画面を、公式APIや他の画面の数字と照合する。

使い方: python compare_pages.py
- 個人・クランランキング: 世界と日本の1000件の並びがAPIと同じか、上位20クランのクラントロフィー
- トッププレイヤーのデッキ: 上位15人のデッキが本人の対戦履歴のランク戦に実在するか、全デッキの平均エリクサーなど
- カード詳細: 使用率・人気順位・一緒に使われるカードを、デッキ画面の全ページから数え直した値と比べる
- クラン情報: 要約文、メンバー一覧(役職・トロフィー・寄付数・最終アクセス)、クラン対戦の参加状況
サイトはAPIの応答を2分キャッシュするので、その間にアクセスや対戦をした人は1〜2人ずれうる。
"""
import collections
import datetime
import re
import time

from common import api, enc, site, text

JAPAN = 57000122
CARDS = [28000011, 26000000, 26000064]  # ローリングウッド・ナイト・ロケット砲士
ROLES = {"leader": "Leader", "coLeader": "Co-leader", "elder": "Elder", "member": "Member"}
BATTLE_DAYS = {"warDay", "colosseum"}


def api_time(s):
    return datetime.datetime.strptime(s, "%Y%m%dT%H%M%S.%fZ").replace(tzinfo=datetime.timezone.utc)


def rankings():
    """タブの中身だけを返す /ranking/players/table・/ranking/table を読む(country 無しは世界)。"""
    global_clans = None
    for scope, location, query in (("世界", "global", ""), ("日本", str(JAPAN), "country=JP&")):
        shown = re.findall(r'href="/player/([0-9A-Z]+)"', site(f"/ranking/players/table?{query}lang=en"))
        want = [p["tag"].lstrip("#") for p in api(f"/locations/{location}/pathoflegend/players?limit=1000")["items"]]
        print(f"[個人ランキング {scope}] 画面 {len(shown)}件 / API {len(want)}件、上位100の並び一致: {shown[:100] == want[:100]}")
        page = site(f"/ranking/table?{query}lang=en")
        global_clans = global_clans or page
        shown = re.findall(r'href="/clan/([0-9A-Z]+)"', page)
        want = [c["tag"].lstrip("#") for c in api(f"/locations/{location}/rankings/clans?limit=1000")["items"]]
        print(f"[クランランキング {scope}] 画面 {len(shown)}件 / API {len(want)}件、上位100の並び一致: {shown[:100] == want[:100]}")
    war_trophies(global_clans)


def war_trophies(page):
    wrong = []
    rows = [r for r in re.findall(r"<tr>(.*?)</tr>", page, re.S) if 'href="/clan/' in r][:20]
    for row in rows:
        tag = re.search(r'href="/clan/([0-9A-Z]+)"', row).group(1)
        cells = [text(c).replace(",", "") for c in re.findall(r"<td[^>]*>(.*?)</td>", row, re.S)]
        war = str((api("/clans/" + enc(tag)) or {}).get("clanWarTrophies"))
        if war not in cells:
            wrong.append((tag, cells, war))
    print(f"  上位{len(rows)}クランのクラントロフィー: 不一致 {len(wrong)}")
    for w in wrong[:3]:
        print("    ", w)


def deck_pages():
    """トッププレイヤーのデッキ画面を全ページ読み、集計日時と [{rank, tag, cards, levels, tower, metrics}] を返す。"""
    decks, collected_at, page = [], None, 1
    while True:
        s = site(f"/decks?lang=en&page={page}")
        # 集計の形式を変えた直後などは「集計しています」だけで、集計日時もデッキも無い。
        found = re.search(r'<time[^>]*datetime="([^"]+)"', s)
        collected_at = collected_at or (found and found.group(1))
        blocks = re.findall(r'<div class="card top-deck">(.*?)(?=<div class="card top-deck">|<nav class="pager")', s, re.S)
        for b in blocks:
            grid, _, support = b.partition('<div class="support-row">')
            tower = re.findall(r'href="/card/(\d+)"', support)
            decks.append({
                "rank": int(re.search(r'top-deck-rank">#(\d+)', b).group(1)),
                "tag": re.search(r'href="/player/([^"?]+)"', b).group(1),
                "cards": [int(x) for x in re.findall(r'href="/card/(\d+)"', grid)],
                "levels": [int(x) for x in re.findall(r'<div class="card-sub">Lv\.(\d+)</div>', grid)],
                "tower": int(tower[0]) if tower else None,
                "metrics": re.findall(r'<span class="deck-metric-value">([^<]*)</span>', b),
            })
        if not blocks or f"page={page + 1}" not in s:
            return (collected_at and datetime.datetime.fromisoformat(collected_at[:19])
                    .replace(tzinfo=datetime.timezone.utc)), decks
        page += 1


def top_decks(collected_at, decks):
    print(f"\n[トッププレイヤーのデッキ] 集計日時 {collected_at} / 掲載 {len(decks)}人")
    ok = 0
    for d in decks[:15]:
        log = api("/players/" + enc(d["tag"]) + "/battlelog") or []
        ranked = [[c["id"] for c in x["team"][0]["cards"]] for x in log if x["type"] == "pathOfLegend"]
        if d["cards"] in ranked:
            ok += 1
        elif log and api_time(log[-1]["battleTime"]) > collected_at:
            print(f"    履歴に無い: {d['tag']}(手元の履歴はすべて集計の後の対戦。押し出されただけ)")
        else:
            print(f"    ★履歴に無い: {d['tag']}(集計の前の対戦も残っているのに見つからない)")
        time.sleep(0.3)
    print(f"  上位15人中 {ok}人のデッキが、本人のランク戦の履歴に並び順まで一致")


def deck_metrics(decks):
    cards = {c["id"]: c for c in api("/cards")["items"]}
    wrong = []
    for d in decks:
        costs = [cards.get(c, {}).get("elixirCost") for c in d["cards"]]
        want = [] if len(costs) != 8 or None in costs else [f"{sum(costs) / 8:.1f}", str(sum(sorted(costs)[:4]))]
        if want and len(d["levels"]) == 8:
            want.append(f"{sum(d['levels']) / 8:.1f}")
        if d["metrics"] != want:
            wrong.append((d["rank"], d["tag"], d["metrics"], want))
    print(f"  平均エリクサー・4枚サイクル・平均レベル: {len(decks)}件中 不一致 {len(wrong)}件")
    for w in wrong[:3]:
        print("    ", w)


def card_usage(decks):
    """CardUsage と同じ数え方で、デッキ画面の全デッキから使用率・順位・一緒に使われるカードを出して比べる。"""
    n = len(decks)
    users, towers, pairs = collections.Counter(), collections.Counter(), collections.defaultdict(collections.Counter)
    for d in decks:
        cards = list(dict.fromkeys(d["cards"]))
        for c in cards:
            users[c] += 1
            pairs[c].update(o for o in cards if o != c)
        if d["tower"]:
            towers[d["tower"]] += 1
    order = [c for c, _ in users.most_common()]
    mid = len(order) // 2
    targets = list(dict.fromkeys(order[:5] + order[mid - 1:mid + 2] + order[-3:] + CARDS
                                 + [c for c, _ in towers.most_common(2)]))
    print(f"\n[カード使用率] デッキ画面の{n}人分から数え直した値とカード詳細画面を比べる({len(targets)}枚)")
    wrong = 0
    for c in targets:
        counter = towers if c in towers else users
        raw = site(f"/card/{c}?lang=en")
        s = text(raw)
        rate = re.search(r"Usage rate ([\d.]+)%", s)
        using = re.search(r"Players using it ([\d,]+) / ([\d,]+)", s)
        pop = re.search(r"Popularity #(\d+) of (\d+)", s)
        # 使用者0人のカードは順位を出さない(CardUsage#usageOf)
        rank = (1 + sum(v > counter[c] for v in counter.values()), len(counter)) if counter[c] else None
        problems = []
        if not rate or abs(float(rate.group(1)) - counter[c] * 100 / n) > 0.05 + 1e-9:
            problems.append(f"使用率 画面={rate and rate.group(1)} 計算={counter[c] * 100 / n:.1f}")
        if not using or (int(using.group(1).replace(",", "")), int(using.group(2).replace(",", ""))) != (counter[c], n):
            problems.append(f"使用人数 画面={using and using.groups()} 計算={(counter[c], n)}")
        if (pop and (int(pop.group(1)), int(pop.group(2)))) != rank:
            problems.append(f"人気順位 画面={pop and pop.groups()} 計算={rank}")
        partners = re.search(r"<h3>.*?</h3>(.*?)<p class=\"notice\">", raw, re.S)
        shown = [] if not partners else list(zip(
            [int(x) for x in re.findall(r'href="/card/(\d+)"', partners.group(1))],
            [int(x) for x in re.findall(r'<div class="card-sub">(\d+)%</div>', partners.group(1))]))
        want = [] if counter is towers or users[c] < 10 else [
            (o, int(v * 100 / users[c] + 0.5)) for o, v in sorted(pairs[c].items(), key=lambda kv: (-kv[1], kv[0]))[:5]]
        if shown != want:
            problems.append(f"一緒に使われるカード 画面={shown} 計算={want}")
        if c in CARDS:
            count = re.search(r"of ([\d,]+)</p>", site(f"/decks?lang=en&card={c}"))
            if not count or int(count.group(1).replace(",", "")) != counter[c]:
                problems.append(f"デッキ画面の絞り込み件数 画面={count and count.group(1)} 計算={counter[c]}")
        wrong += bool(problems)
        for p in problems:
            print(f"    {c}: {p}")
        time.sleep(0.3)
    print(f"  {len(targets)}枚中 不一致 {wrong}枚")


def clans():
    tags = [c["tag"] for c in api("/locations/global/rankings/clans?limit=1")["items"]]
    tags += [c["tag"] for c in api(f"/locations/{JAPAN}/rankings/clans?limit=2")["items"]]
    for tag in tags:
        page = site(f"/clan/{tag.lstrip('#')}?lang=en")
        c = api("/clans/" + enc(tag))
        now = datetime.datetime.now(datetime.timezone.utc)
        print(f"\n[クラン情報] {c['name']}({tag})")
        active = sum(bool(m.get("lastSeen")) and (now - api_time(m["lastSeen"])).days < 7 for m in c["memberList"])
        summary = re.search(r'<p class="page-summary">(.*?)</p>', page, re.S)
        print(f"  要約文「{summary and text(summary.group(1))}」")
        print(f"  API: スコア {c['clanScore']:,} / 人数 {c['members']} / 7日以内のアクセス {active}人 / "
              f"週の寄付合計 {sum(m['donations'] for m in c['memberList']):,}")
        members(page, c, now)
        clan_war(page, c)


def members(page, clan, now):
    table = page.split('id="members"')[1].split("</table>")[0]
    rows = [r for r in re.findall(r"<tr>(.*?)</tr>", table, re.S) if 'href="/player/' in r]
    by_tag = {m["tag"].lstrip("#"): m for m in clan["memberList"]}
    wrong = []
    for row in rows:
        tag = re.search(r'href="/player/([0-9A-Z]+)"', row).group(1)
        cells = [text(c) for c in re.findall(r"<td[^>]*>(.*?)</td>", row, re.S)]
        m = by_tag.get(tag)
        if not m:
            wrong.append((tag, "APIのメンバーにいない"))
            continue
        days = (now - api_time(m["lastSeen"])).days if m.get("lastSeen") else None
        seen = "-" if days is None else "Within 24h" if days == 0 else f"{days} day{'s' if days != 1 else ''} ago"
        want = [ROLES[m["role"]], f"{m['trophies']:,}", f"{m['donations']:,}", seen]
        if cells[1:5] != want:
            wrong.append((tag, cells[1:5], want))
    capped = sum(m["trophies"] == 14000 for m in clan["memberList"])
    print(f"  メンバー一覧: 画面 {len(rows)}人 / API {len(clan['memberList'])}人、不一致 {len(wrong)}人、"
          f"トロフィーが14,000ちょうどの人 {capped}人(14,000で止まる既知の問題)")
    for w in wrong[:3]:
        print("    ", w)


def clan_war(page, clan):
    tag = clan["tag"]
    race = api("/clans/" + enc(tag) + "/currentriverrace")
    section = page.split('id="war"')[1].split("</main>")[0]
    if not race:
        print(f"  クラン対戦: 不参加。画面の案内 {'あり' if 'not taking part' in section else '★なし'}")
        return
    if race.get("periodType") not in BATTLE_DAYS:
        shown = "Clan War battles are not available today" in section
        print(f"  クラン対戦: 今日は攻撃の無い日(periodType={race.get('periodType')})。画面の案内 {'あり' if shown else '★なし'}")
    else:
        participants = {p["tag"].lstrip("#"): p for p in race["clan"]["participants"]}
        want = {}
        for m in clan["memberList"]:
            p = participants.get(m["tag"].lstrip("#"), {})
            want[m["tag"].lstrip("#")] = (p.get("decksUsedToday", 0), p.get("decksUsed", 0))
        totals = [f"{sum(t > 0 for t, _ in want.values())} / {len(want)}",
                  f"{sum(t for t, _ in want.values())} / {len(want) * 4}"]
        grid = re.findall(r'<span class="value">([^<]*)</span>', section.split("</ul>")[0])
        shown = {}
        for row in re.findall(r"<tr>(.*?)</tr>", section, re.S):
            link = re.search(r'href="/player/([0-9A-Z]+)"', row)
            cells = [text(c) for c in re.findall(r"<td[^>]*>(.*?)</td>", row, re.S)]
            if link and len(cells) == 3:
                # 今日の列は「2 / 4」、今週の列は数だけ
                shown[link.group(1)] = (int(cells[1].split("/")[0]), int(cells[2]))
        wrong = [(t, shown.get(t), w) for t, w in want.items() if shown.get(t) != w]
        print(f"  クラン対戦(periodType={race['periodType']}): 今日の集計 {'一致' if grid == totals else '★不一致'} "
              f"画面={grid} 計算={totals}、"
              f"メンバー{len(want)}人中 不一致 {len(wrong)}人")
        for w in wrong[:3]:
            print("    ", w)
    for item in (api("/clans/" + enc(tag) + "/riverracelog?limit=3") or {}).get("items", []):
        mine = next((s for s in item["standings"] if s["clan"]["tag"] == tag), None)
        if not mine:
            continue
        over = sum(p["decksUsed"] > 16 for p in mine["clan"]["participants"])
        print(f"    過去の週 {item['createdDate'][:8]}: decksUsed が16(1日4デッキ×4日)を超えた人 {over}人")


if __name__ == "__main__":
    rankings()
    collected_at, decks = deck_pages()
    if decks:
        top_decks(collected_at, decks)
        deck_metrics(decks)
        card_usage(decks)
    else:
        print("\n[トッププレイヤーのデッキ] 集計中でデッキが無いため、デッキ画面とカード使用率の照合は省いた")
    clans()
