"""本番のプレイヤー情報画面の表示値を、同時に取得した公式APIの値と項目ごとに突き合わせる(転記の正確さの確認)。

使い方: python compare_player.py <タグ> [<タグ> ...]   (#は省略可)
英語表示(lang=en)を読む。サイトはAPIの応答を2分キャッシュするので、その間に対戦した人は戦績がずれうる。
"""
import re
import sys
import time

from common import (FRIENDLY_TYPES, api, both_sides, card_performance, crowns, enc, in_game_level, site, text,
                    win_rate)


def stat_grid(page):
    grid = {}
    block = re.search(r'<ul class="stat-grid">(.*?)</ul>', page, re.S).group(1)
    for li in re.findall(r"<li>(.*?)</li>", block, re.S):
        label = re.search(r'<span class="label">(.*?)</span>\s*<span class="value">', li, re.S)
        value = re.search(r'<span class="value">(.*)</span>', li, re.S)
        if label and value:
            grid[re.sub(r"^\W+", "", text(label.group(1))).strip()] = text(value.group(1))
    return grid


def number(s):
    return int(s.replace(",", "")) if s else None


def shown_performance(page, css):
    """得意(perf-good)・苦手(perf-bad)カードの (カードID, 「88% (15/17)」) の並び。"""
    cards = []
    for block in page.split(f'<div class="deck-card {css}">')[1:]:
        block = block.split('<div class="deck-card')[0]
        cards.append((int(re.search(r'href="/card/(\d+)"', block).group(1)),
                      re.search(r'perf-rate">([^<]*)<', block).group(1)))
    return cards


def opponent_level(battle):
    """対戦履歴の相手の平均カードレベル。2v2と8枚でないデッキは出さない(ViewMapper#toOpponents)。"""
    opponents = battle["opponent"]
    cards = opponents[0].get("cards") or []
    if len(opponents) > 1 or len(cards) != 8:
        return []
    return [f"{sum(in_game_level(c['level'], c['maxLevel']) for c in cards) / 8:.1f}"]


def check(tag):
    page = site(f"/player/{tag.lstrip('#')}?lang=en")
    p = api("/players/" + enc(tag))
    log = api("/players/" + enc(tag) + "/battlelog") or []
    issues = []

    def expect(label, shown, actual):
        if shown != actual:
            issues.append(f"{label}: 画面={shown} API={actual}")

    g = stat_grid(page)
    expect("King Tower Level", number(g.get("King Tower Level")), p["kingTowerLevel"])
    expect("Trophies", number(g.get("Trophies")), p["trophies"])
    expect("Best trophies", number(g.get("Best trophies")), p["bestTrophies"])
    expect("Wins", number(g.get("Wins")), p["wins"])
    expect("Losses", number(g.get("Losses")), p["losses"])
    expect("Three crown wins", number(g.get("Three crown wins")), p["threeCrownWins"])
    raw = p.get("currentWinLoseStreak") or 0
    m = re.search(r"(\d+)", g.get("Streak", ""))
    shown = None if not m else int(m.group(1)) * (1 if "win" in g["Streak"] else -1)
    expect("Streak", shown, raw or None)
    for key, label in (("currentPathOfLegendSeasonResult", "Rank Battle (this season)"),
                       ("bestPathOfLegendSeasonResult", "Rank Battle (best season)")):
        r = p.get(key) or {}
        want = f"Rank {r['rank']} (rating {r['trophies']:,})" if r.get("rank") else None
        expect(label, g.get(label), want)

    battles = [b for b in log if both_sides(b)]
    counted = [b for b in battles if b["type"] not in FRIENDLY_TYPES]
    wins = sum(crowns(b["team"]) > crowns(b["opponent"]) for b in counted)
    losses = sum(crowns(b["team"]) < crowns(b["opponent"]) for b in counted)
    summary = re.search(r'<p class="stats-summary">\s*<span>(.*?)</span>', page, re.S)
    if summary:
        # 英語の文は「{勝}W {敗}L in the last {戦数} battles」の順
        w, l, total = (int(x) for x in re.findall(r"\d+", text(summary.group(1)))[:3])
        expect("stats (total, W, L)", (total, w, l), (len(counted), wins, losses))
    expect("battle rows", len(re.findall(r'<td class="cell-action">', page)), len(battles))
    rows = [r for r in re.findall(r"<tr>(.*?)</tr>", page, re.S) if '<td class="cell-action">' in r]
    wrong_levels = [(b["battleTime"], re.findall(r"Avg\. Lv\.([\d.]+)", r), opponent_level(b))
                    for r, b in zip(rows, battles) if re.findall(r"Avg\. Lv\.([\d.]+)", r) != opponent_level(b)]
    expect("opponent avg level (rows)", wrong_levels[:2], [])

    favorite, weak = card_performance(log)
    as_shown = lambda cards: [(cid, f"{win_rate(w, u)}% ({w}/{u})") for cid, w, u in cards]
    expect("favorite cards", shown_performance(page, "perf-good"), as_shown(favorite))
    expect("weak cards", shown_performance(page, "perf-bad"), as_shown(weak))

    deck = p.get("currentDeck") or []
    section = re.search(r'<div class="card current-deck">(.*?)(?=<div[^>]*class="card[ "])', page, re.S)
    if section and len(deck) == 8:
        s = section.group(1)
        levels = [int(x) for x in re.findall(r'<div class="card-sub">Lv\.(\d+)</div>', s)]
        expect("deck levels", levels, [in_game_level(c["level"], c["maxLevel"]) for c in deck])
        values = re.findall(r'<span class="deck-metric-value">([^<]*)</span>', s)
        costs = [c.get("elixirCost") for c in deck]
        if None not in costs and len(values) >= 3:
            lv = [in_game_level(c["level"], c["maxLevel"]) for c in deck]
            expect("average elixir", values[0], f"{sum(costs) / 8:.1f}")
            expect("4-card cycle", values[1], str(sum(sorted(costs)[:4])))
            expect("average level", values[2], f"{sum(lv) / 8:.1f}")

    print(f"{tag} {p['name']}: {'OK' if not issues else f'{len(issues)}件の食い違い'}")
    for i in issues:
        print("   ", i)
    return not issues


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    ok = 0
    for t in sys.argv[1:]:
        try:
            ok += check(t)
        except Exception as e:  # 1人の失敗で全体を止めない
            print(t, "ERROR", repr(e))
        time.sleep(1)
    print(f"\n{ok}/{len(sys.argv) - 1}人で全項目一致")
