"""プレイヤー情報画面以外の画面を、公式APIや他の画面の数字と照合する。

使い方: python compare_pages.py
- 個人・クランランキング: 1000件の並びがAPIと同じか
- トッププレイヤーのデッキ: 上位15人のデッキが、本人の対戦履歴のランク戦に実在するか
- カード詳細: 使用率が、デッキ画面のカード絞り込みの件数と合うか
- クラン情報: 要約文(スコア・人数・7日以内のアクセス人数)がAPIと合うか
"""
import datetime
import re
import time

from common import api, enc, site, text

JAPAN = 57000122
CARDS = [28000011, 26000000, 26000064]  # ローリングウッド・ナイト・ロケット砲士


def rankings():
    page = site("/ranking/players?lang=en")
    shown = re.findall(r'href="/player/([0-9A-Z]+)"', page)
    want = [p["tag"].lstrip("#") for p in api("/locations/global/pathoflegend/players?limit=1000")["items"]]
    print(f"[個人ランキング] 画面 {len(shown)}件 / API {len(want)}件、上位100の並び一致: {shown[:100] == want[:100]}")
    page = site("/ranking?lang=en")
    shown = re.findall(r'href="/clan/([0-9A-Z]+)"', page)
    want = [c["tag"].lstrip("#") for c in api("/locations/global/rankings/clans?limit=1000")["items"]]
    print(f"[クランランキング] 画面 {len(shown)}件 / API {len(want)}件、上位100の並び一致: {shown[:100] == want[:100]}")


def top_decks():
    page = site("/decks?lang=en")
    collected = re.search(r'<time[^>]*datetime="([^"]+)"', page)
    total = re.search(r'<p class="pager-range">[^<]*of ([\d,]+)</p>', page)
    print(f"\n[トッププレイヤーのデッキ] 集計日時 {collected and collected.group(1)} / 掲載 {total and total.group(1)}人")
    blocks = re.findall(r'<div class="card top-deck">(.*?)(?=<div class="card top-deck">|<nav class="pager")', page, re.S)
    ok = 0
    for b in blocks[:15]:
        tag = re.search(r'href="/player/([^"?]+)"', b).group(1)
        deck = [int(x) for x in re.findall(r'href="/card/(\d+)"', b)][:8]
        log = api("/players/" + enc(tag) + "/battlelog") or []
        ranked = [[c["id"] for c in x["team"][0]["cards"]] for x in log if x["type"] == "pathOfLegend"]
        found = deck in ranked
        ok += found
        if not found:
            print(f"    実在しない: {tag}(集計後に対戦を重ねて履歴から押し出された可能性もある)")
        time.sleep(0.3)
    print(f"  上位15人中 {ok}人のデッキが、本人のランク戦の履歴に並び順まで一致")
    return int(total.group(1).replace(",", "")) if total else None


def card_usage(sample):
    print("\n[カード使用率] カード詳細の使用率 と デッキ画面のカード絞り込みの件数/掲載人数")
    for card in CARDS:
        detail = text(site(f"/card/{card}?lang=en"))
        rate = re.search(r"Usage rate ([\d.]+)%", detail)
        count = re.search(r"of ([\d,]+)</p>", site(f"/decks?lang=en&card={card}"))
        n = int(count.group(1).replace(",", "")) if count else 0
        expected = round(n * 100 / sample, 1) if sample else None
        print(f"  {card}: 使用率 {rate and rate.group(1)}% / 件数から計算 {expected}% ({n}/{sample})")


def clans():
    print("\n[クラン情報] 要約文とAPI")
    tags = [c["tag"] for c in api("/locations/global/rankings/clans?limit=1")["items"]]
    tags += [c["tag"] for c in api(f"/locations/{JAPAN}/rankings/clans?limit=1")["items"]]
    now = datetime.datetime.now(datetime.timezone.utc)
    for tag in tags:
        page = site(f"/clan/{tag.lstrip('#')}?lang=en")
        c = api("/clans/" + enc(tag))
        active = sum(1 for m in c["memberList"]
                     if (now - datetime.datetime.strptime(m["lastSeen"], "%Y%m%dT%H%M%S.%fZ")
                         .replace(tzinfo=datetime.timezone.utc)).days < 7)
        summary = re.search(r'<p class="page-summary">(.*?)</p>', page, re.S)
        print(f"  {c['name']}: 画面「{summary and text(summary.group(1))}」")
        print(f"    API: スコア {c['clanScore']:,} / 人数 {c['members']} / 7日以内のアクセス {active}人 / "
              f"週の寄付合計 {sum(m['donations'] for m in c['memberList']):,}")


if __name__ == "__main__":
    rankings()
    sample = top_decks()
    card_usage(sample)
    clans()
