"""本番VMのデッキ集計のファイルと、画面の使用率の増減・デッキの勝率を照合する。

使い方: python compare_files.py <作業ディレクトリ>
作業ディレクトリに、VMの card-usage/ から次の3つを取ってから動かす(SSHはスクリプトの外で行う。SKILL.md の手順3)。
- top-decks.tsv: 今の集計
- week-ago.tsv: カード一覧画面の注記「since the <月日> update」の日の history/<日付>/<時刻>.tsv
- deck-win-rates.tsv: 勝率の集計(今の集計と同じ回のもの)
デッキ集計が終わった直後(ファイルが替わった直後)に取ると、画面とファイルの回がずれる。注記の集計日時で確かめる。
"""
import collections
import datetime
import os
import re
import sys

from common import site, text


def usage_of(path):
    """CardUsage と同じ数え方の使用率(%)。通常のカードとタワーユニットを別に、1人1回。"""
    users, n = collections.Counter(), 0
    for line in open(path, encoding="utf-8"):
        cols = line.rstrip("\n").split("\t")
        if len(cols) < 3:
            continue
        n += 1
        users.update(set(int(c) for c in cols[0].split(",") if c))
        if cols[1]:
            users[int(cols[1])] += 1
    return {card: u * 100 / n for card, u in users.items()}, n


def half_up(x):
    """ViewMapper#roundPercent(Math.round)と同じ、小数第1位への四捨五入。"""
    return int(x * 10 + 0.5) / 10 if x >= 0 else -int(-x * 10 + 0.5) / 10


def usage_changes(work):
    now, n = usage_of(os.path.join(work, "top-decks.tsv"))
    before, m = usage_of(os.path.join(work, "week-ago.tsv"))
    page = site("/cards?sort=usage&lang=en")
    notice = re.search(r"since the ([A-Z][a-z]+ \d+) update", text(page))
    wrong, checked = [], 0
    for block in page.split('<div class="deck-card"')[1:]:
        card = int(re.search(r'href="/card/(\d+)"', block).group(1))
        rate = re.search(r'<div class="card-sub card-usage">.*?<span>([\d.]+)%</span>', block, re.S)
        change = re.search(r'<span\s+aria-hidden="true">([▲▼±][\d.]+)</span>', block)
        if not rate:
            continue
        checked += 1
        delta = half_up(half_up(now.get(card, 0)) - half_up(before.get(card, 0)))
        want = f"▲{delta:.1f}" if delta > 0 else f"▼{-delta:.1f}" if delta < 0 else "±0"
        if float(rate.group(1)) != half_up(now.get(card, 0)) or (change and change.group(1)) != want:
            wrong.append((card, rate.group(1), change and change.group(1), f"{half_up(now.get(card, 0))}", want))
    print(f"[使用率の増減] 今 {n}人・比べた集計 {m}人(画面の注記: {notice and notice.group(1)})。"
          f"{checked}枚中 使用率か増減の不一致 {len(wrong)}枚")
    for w in wrong[:5]:
        print("    カード, 画面の使用率, 画面の増減, 計算の使用率, 計算の増減:", w)


def win_rates(work):
    lines = open(os.path.join(work, "deck-win-rates.tsv"), encoding="utf-8").read().splitlines()
    header, rows = lines[0].split("\t"), [line.split("\t") for line in lines[1:]]
    counted = int(header[1])
    decks = [{"cards": frozenset(int(c) for c in r[0].split(",")), "battles": int(r[3]), "wins": int(r[4]),
              "players": int(r[5]), "max": int(r[6]), "tag": r[7].lstrip("#")} for r in rows]
    listed = [d for d in decks if d["battles"] >= 50 and d["players"] >= 10 and d["max"] * 100 <= d["battles"] * 30]
    page = site("/decks/win-rates?lang=en")
    shown_counted = re.search(r"Battles counted: ([\d,]+)", text(page))
    shown = set()
    for b in page.split('<div class="card top-deck popular-deck">')[1:]:
        grid = b.split('<div class="support-row">')[0]
        shown.add(frozenset(int(x) for x in re.findall(r'href="/card/(\d+)"', grid)))
    print(f"\n[勝率の集計ファイル] 集計 {header[0][:16]}、全{len(decks)}デッキのうち条件を満たす {len(listed)}件 / 画面 {len(shown)}件、"
          f"同じデッキの集合: {shown == {d['cards'] for d in listed}}")
    print(f"  数えた対戦: 画面 {shown_counted and shown_counted.group(1)} / ファイル {counted:,}")
    # 1戦を両者の側から1回ずつ数え、同じデッキどうしは除く(1.178)なら、全デッキの戦数の合計は数えた対戦の2倍、
    # 勝ちの合計は引き分けを除いた対戦の数になる
    battles, wins = sum(d["battles"] for d in decks), sum(d["wins"] for d in decks)
    print(f"  全デッキの戦数の合計 {battles:,}(数えた対戦の2倍は {2 * counted:,})、勝ちの合計 {wins:,}"
          f"(引き分けを除いた対戦の数のはず。数えた対戦の {100 * wins / counted:.1f}%)")
    excluded = [d for d in decks if d["battles"] >= 50 and d["players"] >= 10 and d["max"] * 100 > d["battles"] * 30]
    print(f"  50戦・10人以上だが1人の対戦が3割を超えて載せないデッキ {len(excluded)}件"
          + (f"(例: {excluded[0]['battles']}戦のうち1人が{excluded[0]['max']}戦)" if excluded else ""))


def top_battles(work, since, until):
    """top-battles/<日付>.tsv(1行1戦: 時刻・両者のタグ・8枚・クラウン・消費漏れ)の、since〜until の対戦。"""
    battles = []
    folder = os.path.join(work, "top-battles")
    for name in sorted(os.listdir(folder)):
        for line in open(os.path.join(folder, name), encoding="utf-8"):
            c = line.rstrip("\n").split("\t")
            at = datetime.datetime.fromisoformat(c[0].replace("Z", "+00:00"))
            if since <= at <= until:
                battles.append(((set(int(x) for x in c[2].split(",")), int(c[3]), float(c[4])),
                                (set(int(x) for x in c[6].split(",")), int(c[7]), float(c[8]))))
    return battles


def wilson_lower(wins, n, z=1.96):
    p = wins / n
    return (p + z * z / (2 * n) - z * (p * (1 - p) / n + z * z / (4 * n * n)) ** 0.5) / (1 + z * z / n)


def matchups(work, cards):
    """カード詳細画面の「〇〇入りのデッキが得意なカード」「〇〇入りのデッキが苦手なカード」(1.179・1.195)を、
    CardMatchups と同じ規則で数え直す。勝率はどちらも〇〇入りのデッキの側から見たもの。
    数えるのは画面の期間(最も古い〜最も新しい対戦)の、引き分けでない対戦。両方のデッキにあるカードは数えない。"""
    for card in cards:
        page = site(f"/card/{card}?lang=en")
        section = page.split('id="matchups"')
        if len(section) < 2:
            print(f"  {card}: 欄なし")
            continue
        section = section[1].split("</section>")[0]
        since, until = [datetime.datetime.fromisoformat(t.replace("Z", "+00:00"))
                        for t in re.findall(r'<time[^>]*datetime="([^"]+)"', section)[:2]]
        total, pairs = [0, 0], collections.defaultdict(lambda: [0, 0])
        for a, b in top_battles(work, since, until):
            if a[1] == b[1]:
                continue
            for side, other, won in ((a, b, a[1] > b[1]), (b, a, b[1] > a[1])):
                if card in side[0] and card not in other[0]:
                    total[0] += 1
                    total[1] += won
                    for y in other[0] - side[0]:
                        pairs[y][0] += 1
                        pairs[y][1] += won
        rate = lambda w, n: int(100 * w / n + 0.5)
        base = rate(total[1], total[0])
        eligible = [(y, n, w) for y, (n, w) in pairs.items() if n >= 100]
        want = {
            "得意": sorted((t for t in eligible if rate(t[2], t[1]) > base),
                         key=lambda t: (-wilson_lower(t[2], t[1]), -t[1], t[0]))[:5],
            "苦手": sorted((t for t in eligible if rate(t[2], t[1]) < base),
                         key=lambda t: (-wilson_lower(t[1] - t[2], t[1]), -t[1], t[0]))[:5],
        }
        want = {k: [(y, f"{rate(w, n)}% ({n:,} battles)") for y, n, w in v] for k, v in want.items()}
        # 苦手の欄(id="counters")より前が得意の欄。どちらも無い欄は出ない。
        parts = dict(zip(("得意", "苦手"), section.split('id="counters"'))) if 'id="counters"' in section \
            else {"得意": section, "苦手": ""}
        shown = {k: list(zip([int(x) for x in re.findall(r'href="/card/(\d+)"', part)],
                             [text(x) for x in re.findall(r'<div class="card-sub perf-rate">(.*?)</div>', part, re.S)]))
                 for k, part in parts.items()}
        baseline = re.search(r"Overall, decks with .*? had a win rate of (\d+)% \(([\d,]+) battles?\)", text(section))
        want_baseline = (str(base), f"{total[0]:,}")
        ok = shown == want and baseline and baseline.groups() == want_baseline
        print(f"  {card}: {'一致' if ok else '★不一致'} 画面 {shown} {baseline and baseline.groups()} / 計算 {want} {want_baseline}"
              if not ok else f"  {card}: 一致(得意{len(shown['得意'])}枚・苦手{len(shown['苦手'])}枚、"
                             f"全体 {want_baseline[1]}戦 {want_baseline[0]}%)")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    usage_changes(sys.argv[1])
    win_rates(sys.argv[1])
    print("\n[カードの相性] top-battles/ から数え直す")
    matchups(sys.argv[1], [26000010, 26000000, 26000064, 28000011, 26000021, 26000107, 26000049, 27000000])
