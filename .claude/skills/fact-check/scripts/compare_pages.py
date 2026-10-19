"""プレイヤー情報画面以外の画面を、公式APIや他の画面の数字と照合する。

使い方: python compare_pages.py
- 個人・クランランキング: 世界と日本の1000件の並びがAPIと同じか、上位20クランのクラントロフィー
- トッププレイヤーのデッキ: 上位15人のデッキが本人の対戦履歴のランク戦に実在するか、全デッキの平均エリクサーなど
- カード詳細: 使用率・人気順位・一緒に使われるカードを、デッキ画面の全ページから数え直した値と比べる
- クラン情報: 要約文、メンバー一覧(順位・役職・トロフィー・寄付数・最終アクセス)、クラン対戦の参加状況
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
# 国名はCLDRの短い表記(CountryNames)なので、公式APIの英語名と違うものがある
SHORT_NAMES = {"United Kingdom": "UK", "United States": "US"}


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


def ranking_rows(table_html):
    """ランキング表の各行の (タグ, 数字のセル)。"""
    rows = []
    for row in re.findall(r"<tr>(.*?)</tr>", table_html, re.S):
        tag = re.search(r'href="/player/([0-9A-Z]+)"', row)
        if tag:
            rows.append((tag.group(1), [text(c) for c in re.findall(r'<td class="num"[^>]*>(.*?)</td>', row, re.S)]))
    return rows


def season_standings():
    """シーズンの最終順位画面(1.154)を、終わったシーズンのランキングAPIと比べる。"""
    season = api("/locations/global/seasons")["items"][-1]["id"]
    shown = ranking_rows(site(f"/ranking/players/seasons/{season}?lang=en"))
    want = [(p["tag"].lstrip("#"), f"{p['eloRating']:,}")
            for p in api(f"/locations/global/pathoflegend/{season}/rankings/players?limit=1000")["items"]]
    got = [(tag, cells[-1] if cells else None) for tag, cells in shown]
    print(f"\n[シーズンの最終順位 {season}] 画面 {len(got)}人 / API {len(want)}人、並びとレーティングの一致: {got == want}")
    for g, w in [(g, w) for g, w in zip(got, want) if g != w][:3]:
        print("    ", g, w)


def movements():
    """ランキングの動き画面(1.133)のトップ10と急上昇を、本人の対戦履歴で確かめる(値の意味の照合)。
    比べた2つの記録の間のランク戦の trophyChange の合計が、画面の増減と同じになるはず。
    対戦履歴(約30戦)が比べた記録の時刻まで遡れない人は数えない。記録の直前の対戦は、ランキングAPIへの反映が遅れて
    次の記録に回ることがあるので、ずれた人は対戦の時刻と一緒に出す。"""
    page = site("/ranking/players/movements?lang=en")
    times = [datetime.datetime.fromisoformat(t.replace("Z", "+00:00"))
             for t in re.findall(r'<time[^>]*datetime="([^"]+)"', page.split("<section")[0])[:2]]
    if len(times) < 2:
        print("\n[ランキングの動き] 比べる記録が無い(シーズンの最初の記録)ので省いた")
        return
    recorded, compared = times
    tables = page.split('<table class="table-ranking">')
    top = ranking_rows(tables[1].split("</table>")[0])
    now = {p["tag"].lstrip("#"): (p["rank"], p["eloRating"]) for p in
           api("/locations/global/pathoflegend/players?limit=1000")["items"]}
    print(f"\n[ランキングの動き] 記録 {recorded:%m-%d %H:%M} UTC と {compared:%m-%d %H:%M} UTC の比較。"
          f"トップ{len(top)}人のうち、今のランキングAPIと順位・レーティングが同じ人 "
          f"{sum(now.get(tag) == (i + 1, int(cells[0].replace(',', ''))) for i, (tag, cells) in enumerate(top))}人"
          "(記録は最大15分前なので、その後の対戦でずれうる)")
    climbers = []
    # 急上昇の表は、トップ10の後の表のうち最初に「→」(順位の移り)が出る表。トップ10から外れた人の表はその後
    for t in tables[2:]:
        body = t.split("</table>")[0]
        if "→" in body:
            climbers = [(tag, cells[-1]) for tag, cells in ranking_rows(body)]
            break
    checked = ok = 0
    for tag, change in [(tag, cells[1]) for tag, cells in top if len(cells) > 1] + climbers:
        if not change:
            continue
        log = api("/players/" + enc(tag) + "/battlelog") or []
        ranked = [b for b in log if b["type"] == "pathOfLegend" and b["team"][0].get("tag") == "#" + tag]
        if not log or api_time(log[-1]["battleTime"]) > compared:
            continue
        inside = [b for b in ranked if compared < api_time(b["battleTime"]) <= recorded]
        total = sum(b["team"][0].get("trophyChange") or 0 for b in inside)
        checked += 1
        want = int(change.replace(",", "").replace("±", "").replace("+", ""))
        if total == want:
            ok += 1
        else:
            edge = [f"{api_time(b['battleTime']):%H:%M}({b['team'][0].get('trophyChange'):+})" for b in inside[:1] + inside[-1:]]
            print(f"    {tag}: 画面 {change} / 対戦履歴の合計 {total:+}(間の対戦 {len(inside)}戦。最新と最古 {edge})")
        time.sleep(0.3)
    print(f"  トップ10と急上昇のうち、対戦履歴が記録の時刻まで遡れる {checked}人中 {ok}人で、"
          "増減が間のランク戦の trophyChange の合計と一致")


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
    top = api("/locations/global/pathoflegend/players?limit=1000")["items"]
    ranking = {p["tag"]: p["rank"] for p in top}
    # 上位のクランはクランスコアの順で、世界ランカーがいないことがある。世界順位のバッジを見るため、世界1位のクランも足す
    tags += [c["tag"] for c in [top[0].get("clan")] if c and c["tag"] not in tags]
    for tag in tags:
        page = site(f"/clan/{tag.lstrip('#')}?lang=en")
        c = api("/clans/" + enc(tag))
        now = datetime.datetime.now(datetime.timezone.utc)
        print(f"\n[クラン情報] {c['name']}({tag})")
        active = sum(bool(m.get("lastSeen")) and (now - api_time(m["lastSeen"])).days < 7 for m in c["memberList"])
        summary = re.search(r'<meta name="description" content="(.*?)">', page, re.S)
        print(f"  要約文「{summary and text(summary.group(1))}」")
        print(f"  API: スコア {c['clanScore']:,} / 人数 {c['members']} / 7日以内のアクセス {active}人 / "
              f"週の寄付合計 {sum(m['donations'] for m in c['memberList']):,}")
        join_conditions(page, c)
        members(page, c, now)
        world_rank_badges(page, c, ranking)
        war_history(page, tag)
        clan_war(page, c)


def join_conditions(page, clan):
    """基本情報と参加条件(1.125・1.169)。所在地は英語表示ではほぼ公式APIの名前のまま(SHORT_NAMES を除く)。"""
    location = (clan.get("location") or {}).get("name")
    grid = {}
    block = page.split('<ul class="stat-grid">')[1].split("</ul>")[0]
    for li in re.findall(r"<li>(.*?)</li>", block, re.S):
        label = re.search(r'<span class="label">(.*?)</span>\s*<span class="value"', li, re.S)
        value = re.search(r'<span class="value">(.*?)</span>', li, re.S)
        if label and value:
            grid[re.sub(r"^\W+", "", text(label.group(1)))] = text(value.group(1))
    types = {"open": "Open", "inviteOnly": "Invite Only", "closed": "Closed"}
    want = {"Clan score": f"{clan['clanScore']:,}", "Clan trophies": f"{clan['clanWarTrophies']:,}",
            "Members": str(clan["members"]), "Type": types.get(clan.get("type")),
            "Required Trophies": f"{clan['requiredTrophies']:,}",
            "Clan location": SHORT_NAMES.get(location, location),
            "Donations/Week": f"{clan['donationsPerWeek']:,}"}
    wrong = {k: (grid.get(k), v) for k, v in want.items() if grid.get(k) != v}
    print(f"  基本情報と参加条件: {len(want)}項目中 不一致 {len(wrong)}" + (f" {wrong}" if wrong else ""))


def world_rank_badges(page, clan, ranking):
    """メンバーの「#12 worldwide」(1.162)。15分ごとの記録から出すので、今のランキングAPIとは数人ずれうる。
    ずれたら、VMの最新の記録(ranking-history/global/<日付>/<時刻>.tsv。「順位 タグ レーティング 名前」)と比べる(10/19は57人で一致)。"""
    table = page.split('id="members"')[1].split("</table>")[0]
    shown = {}
    for row in re.findall(r"<tr>(.*?)</tr>", table, re.S):
        tag = re.search(r'href="/player/([0-9A-Z]+)"', row)
        badge = re.search(r'<span class="world-rank-badge">#([\d,]+) worldwide</span>', row)
        if tag:
            shown[tag.group(1)] = int(badge.group(1).replace(",", "")) if badge else None
    want = {m["tag"].lstrip("#"): ranking.get(m["tag"]) for m in clan["memberList"]}
    wrong = [(t, shown.get(t), w) for t, w in want.items() if shown.get(t) != w]
    print(f"  世界順位のバッジ: 画面 {sum(v is not None for v in shown.values())}人 / 今のランキングAPI "
          f"{sum(v is not None for v in want.values())}人、不一致 {len(wrong)}人" + (f" 例 {wrong[:3]}" if wrong else ""))


def war_history(page, tag):
    """クラン対戦成績(1.177): riverracelog の週ごとの順位・メダル・クラントロフィーの増減。メダル0の週は「—」。"""
    section = page.split('id="war-history"')[1].split('id="war"')[0]
    shown = [[text(c) for c in re.findall(r"<td[^>]*>(.*?)</td>", r, re.S)] for r in re.findall(r"<tr>(.*?)</tr>", section, re.S)]
    shown = [r for r in shown if r]
    want = []
    for item in (api("/clans/" + enc(tag) + "/riverracelog") or {}).get("items", []):
        s = next((s for s in item["standings"] if s["clan"]["tag"] == tag), None)
        if s:
            change, ended = s["trophyChange"], api_time(item["createdDate"])
            want.append([f"{ended:%b} {ended.day}",
                         str(s["rank"]) if s["clan"]["fame"] > 0 else "—", f"{s['clan']['fame']:,}",
                         f"+{change:,}" if change > 0 else "±0" if change == 0 else f"{change:,}"])
    firsts = sum(w[1] == "1" for w in want)
    streak = next((i for i, w in enumerate(want) if w[1] != "1"), len(want))
    summary = re.findall(r'<span class="value">([^<]*)</span>', section)
    want_summary = ([f"{firsts} of {len(want)} week{'s' if len(want) != 1 else ''}"] if firsts else []) + (
        [f"{streak} weeks"] if streak >= 2 else [])
    print(f"  クラン対戦成績: {len(want)}週、表の一致 {shown == want}、1位の回数・連続1位の一致 {summary == want_summary}"
          f"(画面 {summary} / 計算 {want_summary})")
    for g, w in [(g, w) for g, w in zip(shown, want) if g != w][:3]:
        print("    ", g, w)


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
        want = [str(m["clanRank"]), ROLES[m["role"]], f"{m['trophies']:,}", f"{m['donations']:,}", seen]
        # 1列目がゲーム内のクランの順位、2列目が名前(スマホ幅用に順位も中に入っているので比べない)。
        got = cells[:1] + cells[2:6]
        if got != want:
            wrong.append((tag, got, want))
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
        # 10/17から、今週1回も攻撃していない人は表に入れず、表の下に名前だけを並べる
        listed = section.split('class="name-list war-not-attacked">')
        not_attacked = set(re.findall(r'href="/player/([0-9A-Z]+)"', listed[1].split("</p>")[0])) if len(listed) > 1 else set()
        wrong = [(t, shown.get(t), w) for t, w in want.items()
                 if (w[1] > 0 and shown.get(t) != w) or (w[1] == 0 and (t in shown or t not in not_attacked))]
        print(f"  クラン対戦(periodType={race['periodType']}): 今日の集計 {'一致' if grid == totals else '★不一致'} "
              f"画面={grid} 計算={totals}、"
              f"メンバー{len(want)}人(表 {len(shown)}人・未攻撃の欄 {len(not_attacked)}人)中 不一致 {len(wrong)}人")
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
    season_standings()
    movements()
    collected_at, decks = deck_pages()
    if decks:
        top_decks(collected_at, decks)
        deck_metrics(decks)
        card_usage(decks)
    else:
        print("\n[トッププレイヤーのデッキ] 集計中でデッキが無いため、デッキ画面とカード使用率の照合は省いた")
    clans()
