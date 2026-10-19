"""本番のプレイヤー情報画面の表示値を、同時に取得した公式APIの値と項目ごとに突き合わせる(転記の正確さの確認)。

使い方: python compare_player.py <タグ> [<タグ> ...]   (#は省略可)
英語表示(lang=en)を読む。サイトはAPIの応答を2分キャッシュするので、その間に対戦した人は戦績がずれうる。
"""
import decimal
import re
import sys
import time

from common import (api, both_sides, card_performance, counted, enc, in_game_level, result, score, site, streak,
                    text, win_rate)


def stat_grid(page):
    """基本情報の項目名→表示値。2026-10-03から大きく出す上段(stat-highlights)と下段(stat-grid-compact)に分かれた。
    ランク戦の順位のように補足(レーティング)を添える項目は「Rank 2 / Rating 3,507」の形で返す。"""
    grid = {}
    for block in re.findall(r'<ul class="stat-(?:highlights|grid stat-grid-compact)">(.*?)</ul>', page, re.S):
        for li in re.findall(r"<li>(.*?)</li>", block, re.S):
            label = re.search(r'<span class="label">(.*?)</span>\s*<span class="value">', li, re.S)
            value = re.search(r'<span class="value">(.*?)</span>', li, re.S)
            sub = re.search(r'<span class="sub">(.*?)</span>', li, re.S)
            if label and value:
                shown = text(value.group(1)) + (" / " + text(sub.group(1)) if sub else "")
                grid[re.sub(r"^\W+", "", text(label.group(1))).strip()] = shown
    return grid


def number(s):
    return int(s.replace(",", "")) if s and s != "-" else None


def shown_performance(page, css):
    """得意(perf-good)・苦手(perf-bad)カードの (カードID, 「88% (15/17)」) の並び。"""
    cards = []
    for block in page.split(f'<div class="deck-card {css}">')[1:]:
        block = block.split('<div class="deck-card')[0]
        cards.append((int(re.search(r'href="/card/(\d+)"', block).group(1)),
                      re.search(r'perf-rate">([^<]*)<', block).group(1)))
    return cards


def opponent_level(battle):
    """対戦履歴の相手の平均カードレベル。2v2と8枚でないデッキは出さない(ViewMapper#toOpponents)。
    デュエルは全ラウンドのデッキの平均で、8枚でないラウンドが1つでもあれば出さない(1.180)。"""
    opponents = battle["opponent"]
    if len(opponents) > 1:
        return []
    decks = [r.get("cards") or [] for r in opponents[0].get("rounds") or []] or [opponents[0].get("cards") or []]
    if any(len(cards) != 8 for cards in decks):
        return []
    averages = [sum(in_game_level(c["level"], c["maxLevel"]) for c in cards) / 8 for cards in decks]
    return [f"{sum(averages) / len(averages):.1f}"]


def check(tag):
    page = site(f"/player/{tag.lstrip('#')}?lang=en")
    p = api("/players/" + enc(tag))
    log = api("/players/" + enc(tag) + "/battlelog") or []
    issues = []

    def expect(label, shown, actual):
        if shown != actual:
            issues.append(f"{label}: 画面={shown} API={actual}")

    g = stat_grid(page)
    # 0(導入以降ログインしていないアカウント)は画面では「-」と出す(ViewMapper#kingTowerLevel)
    expect("King Tower Level", number(g.get("King Tower Level")), p["kingTowerLevel"] or None)
    expect("Trophies", number(g.get("Trophies")), p["trophies"])
    expect("Best trophies", number(g.get("Best trophies")), p["bestTrophies"])
    expect("Wins", number(g.get("Wins")), p["wins"])
    expect("Losses", number(g.get("Losses")), p["losses"])
    expect("Three crown wins", number(g.get("Three crown wins")), p["threeCrownWins"])
    # 連勝・連敗は2026-10-01から、公式APIの currentWinLoseStreak ではなく対戦履歴から数えている(WinLoseStreak)。
    # 取得できた対戦がすべて同じ結果なら「At least N ...」と出る。
    counted_results = [result(b) for b in counted(log)]
    s = streak(counted_results)
    want = (s, abs(s) == len(counted_results)) if s else None
    m = re.search(r"(\d+)", g.get("Streak", ""))
    shown = None if not m else (int(m.group(1)) * (1 if "win" in g["Streak"] else -1),
                                g["Streak"].startswith("At least"))
    expect("Streak (count, at least)", shown, want)
    for key, label in (("currentPathOfLegendSeasonResult", "Rank Battle (this season)"),
                       ("bestPathOfLegendSeasonResult", "Rank Battle (best past season)")):
        r = p.get(key) or {}
        # 10/12から「#15 worldwide」。レーティング0(シーズンの切り替え直後)は順位を出さない(RankedSeasonResult#hasRank)
        want = f"#{r['rank']:,} worldwide / Rating {r['trophies']:,}" if r.get("rank") and r.get("trophies") else None
        if want is None and key == "currentPathOfLegendSeasonResult":
            # 10/14から順位の無い人は今シーズンのリーグ。1(マスターI)は14,000の人だけ(RankedLeague#currentOf)
            league = r.get("leagueNumber") if r.get("leagueNumber") in LEAGUES else None
            league = None if league == 1 and p["trophies"] < 14000 else league
            want = league and LEAGUES[league] + (f" / Rating {r['trophies']:,}" if league == 7 and r.get("trophies") else "")
        expect(label, g.get(label), want)

    battles = [b for b in log if both_sides(b)]
    stats_battles = counted(log)
    wins = sum(result(b) == "W" for b in stats_battles)
    losses = sum(result(b) == "L" for b in stats_battles)
    summary = re.search(r'<p class="stats-summary">\s*<span>(.*?)</span>', page, re.S)
    if summary:
        # 英語の文は「{勝}W {敗}L in the last {戦数} battles」の順
        w, l, total = (int(x) for x in re.findall(r"\d+", text(summary.group(1)))[:3])
        expect("stats (total, W, L)", (total, w, l), (len(stats_battles), wins, losses))
    # 対戦履歴は2026-10-03から表ではなく、1戦1つの<li class="battle-item ...">の一覧
    rows = [item.split("</li>")[0] for item in page.split('<li class="battle-item')[1:]]
    expect("battle rows", len(rows), len(battles))
    wrong_levels = [(b["battleTime"], re.findall(r"Avg\. Lv\.([\d.]+)", r), opponent_level(b))
                    for r, b in zip(rows, battles) if re.findall(r"Avg\. Lv\.([\d.]+)", r) != opponent_level(b)]
    expect("opponent avg level (rows)", wrong_levels[:2], [])
    # 2026-10-04から各対戦の増減、10-14からランク戦のリーグ、10-18からデュエルはラウンドの勝ち数を出す
    wrong_rows = [(b["battleTime"], got, want) for r, b in zip(rows, battles)
                  if (got := row_facts(r)) != (want := expected_row(b, p["tag"]))]
    expect("score / trophy change / league (rows)", wrong_rows[:2], [])
    expect("elixir leaked (this player)", shown_leak(page), expected_leak(log))

    favorite, weak = card_performance(log)
    as_shown = lambda cards: [(cid, f"{win_rate(w, u)}% ({w}/{u})") for cid, w, u in cards]
    expect("favorite cards", shown_performance(page, "perf-good"), as_shown(favorite))
    expect("weak cards", shown_performance(page, "perf-bad"), as_shown(weak))

    deck = shown_deck(p, log)
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


LEAGUES = {1: "Master I", 2: "Master II", 3: "Master III", 4: "Champion", 5: "Grand Champion",
           6: "Royal Champion", 7: "Ultimate Champion"}


def row_facts(row):
    """対戦履歴の1行の (スコア, 増減, リーグ)。"""
    shown_score = re.search(r'<span class="battle-crowns">.*?</span>\s*<span>([^<]*)</span>', row, re.S)
    change = re.search(r'<span class="trophy-change[^"]*"><span>([^<]*)</span> <strong>([^<]*)</strong>', row)
    mode = re.search(r'<div class="battle-sub">\s*<span>([^<]*)</span>', row)
    league = mode and re.search(r"\((.*)\)$", text(mode.group(1)))
    return (shown_score and text(shown_score.group(1)), change and f"{change.group(1)} {change.group(2)}",
            league and league.group(1) if mode and text(mode.group(1)).startswith("Rank Battle") else None)


def expected_row(b, me):
    """TrophyChange#of・ViewMapper#gameModeOf と同じ。増減は PvP(トロフィー)と、天界(7)かリーグ不明のランク戦(レーティング)だけ。"""
    t, o = score(b)
    viewer = next((x for x in b["team"] if x.get("tag") == me), None)
    amount = (viewer or {}).get("trophyChange")
    league = b.get("leagueNumber") if b["type"] == "pathOfLegend" and b.get("leagueNumber") in LEAGUES else None
    label = ("Trophies" if b["type"] == "PvP" else "Rating" if b["type"] == "pathOfLegend" and league in (None, 7)
             else None)
    change = f"{label} {amount:+,}" if label and amount else None
    return f"{t} - {o}", change, LEAGUES.get(league)


def shown_leak(page):
    """消費漏れの表の本人の行 (対戦数, 勝ちの中央値, 負けの中央値)。表が無ければ None。"""
    table = re.search(r'<table class="table-compact leak-table">.*?<tbody>\s*<tr>(.*?)</tr>', page, re.S)
    if not table:
        return None
    count = re.search(r"\((\d+) battles?\)", text(table.group(1)))
    cells = [text(c) for c in re.findall(r'<td class="num">(.*?)</td>', table.group(1), re.S)]
    return (int(count.group(1)) if count else None, *cells[:2])


def expected_leak(log):
    """ElixirLeak#ofPlayer と同じ。ランク戦とトロフィー目標(Ladder)の1対1だけ、引き分けは除き、3戦未満は「-」。
    Thymeleaf の formatDecimal は HALF_EVEN で丸める。"""
    by_result = {"W": [], "L": []}
    for b in log:
        if not both_sides(b) or len(b["team"]) != 1 or len(b["opponent"]) != 1:
            continue
        if b["type"] != "pathOfLegend" and (b.get("gameMode") or {}).get("name") != "Ladder":
            continue
        leaked, r = b["team"][0].get("elixirLeaked"), result(b)
        if leaked is not None and r in by_result:
            by_result[r].append(leaked)

    def median(values):
        if len(values) < 3:
            return "-"
        v, mid = sorted(values), len(values) // 2
        m = v[mid] if len(v) % 2 else (v[mid - 1] + v[mid]) / 2
        return str(decimal.Decimal(repr(m)).quantize(decimal.Decimal("0.1"), rounding=decimal.ROUND_HALF_EVEN))

    battles = len(by_result["W"]) + len(by_result["L"])
    if len(by_result["W"]) < 3 and len(by_result["L"]) < 3:
        return None
    return battles, median(by_result["W"]), median(by_result["L"])


def shown_deck(p, log):
    """画面の使用中のデッキ: 直近のトロフィー戦・ランク戦の本人の8枚(レベルは所持カードの今の値)。無ければ currentDeck。"""
    owned = {c["id"]: c for c in p.get("cards") or []}
    me = p["tag"].lstrip("#").upper()
    for b in log:
        standard = b.get("type") == "pathOfLegend" or (b.get("gameMode") or {}).get("name") == "Ladder"
        if not standard or len(b.get("team") or []) != 1 or len(b.get("opponent") or []) != 1:
            continue
        t = b["team"][0]
        if (t.get("tag") or "").lstrip("#").upper() != me or len(t.get("cards") or []) != 8:
            continue
        return [dict(c, **{k: owned[c["id"]][k] for k in ("level", "maxLevel")}) if c["id"] in owned else c
                for c in t["cards"]]
    return p.get("currentDeck") or []


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
