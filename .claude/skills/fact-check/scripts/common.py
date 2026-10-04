"""fact-check スキルのスクリプトで共通に使う、公式API・本番サイトの取得処理。"""
import html
import json
import os
import re
import time
import urllib.error
import urllib.parse
import urllib.request

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
SITE = "https://princess-tower.duckdns.org"
# access-check のログ集計で見分けられるよう、サイトへのアクセスには専用のUser-Agentを付ける。
USER_AGENT = "princess-tower-factcheck"
# ゲーム内のカードレベルの上限(CardLevel.MAX_LEVEL と同じ)。
MAX_LEVEL = 16
FRIENDLY_TYPES = {"clanMate", "clanMate2v2", "friendly"}


def token():
    if os.environ.get("CLASHROYALE_API_TOKEN"):
        return os.environ["CLASHROYALE_API_TOKEN"]
    cfg = open(os.path.join(REPO, "config", "application-local.yml"), encoding="utf-8").read()
    return re.search(r"token:\s*['\"]?([^\s'\"]+)", cfg).group(1)


_TOKEN = None


def api(path):
    """公式APIを呼ぶ。404はNone。429は少し待って取り直す。"""
    global _TOKEN
    _TOKEN = _TOKEN or token()
    req = urllib.request.Request("https://api.clashroyale.com/v1" + path,
                                 headers={"Authorization": "Bearer " + _TOKEN})
    for _ in range(3):
        try:
            with urllib.request.urlopen(req, timeout=20) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            if e.code == 429:
                time.sleep(3)
                continue
            if e.code == 403:
                raise SystemExit("公式APIが403を返した。ローカルのグローバルIPがAPIキーの許可リストから外れていないか確認する")
            raise
    return None


def site(path):
    req = urllib.request.Request(SITE + path, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read().decode("utf-8")


def enc(tag):
    return urllib.parse.quote(tag if tag.startswith("#") else "#" + tag)


def text(fragment):
    """HTML片からタグを除き、名前の前後に入れている分離文字(U+2068/U+2069)も外す。"""
    s = html.unescape(re.sub(r"<[^>]+>", " ", fragment))
    return re.sub(r"\s+", " ", s.replace("⁨", "").replace("⁩", "")).strip()


def in_game_level(level, max_level):
    """CardLevel.inGame と同じ換算。"""
    return level if max_level <= 0 or max_level > MAX_LEVEL else level + MAX_LEVEL - max_level


def crowns(side):
    return max(p.get("crowns", 0) for p in side)


def result(battle):
    t, o = crowns(battle["team"]), crowns(battle["opponent"])
    return "W" if t > o else "L" if t < o else "D"


def both_sides(battle):
    return bool(battle.get("team")) and bool(battle.get("opponent"))


def counted(battlelog):
    """戦績サマリーと連勝・連敗で数える対戦(BattleExclusion と同じ)。フレンドバトルと船のバトルの守備側を除く。"""
    return [b for b in battlelog if both_sides(b) and b["type"] not in FRIENDLY_TYPES
            and b.get("boatBattleSide") != "defender"]


def _wilson(wins, uses, sign, z=1.96):
    p = wins / uses
    centre = p + z * z / (2 * uses)
    margin = z * (p * (1 - p) / uses + z * z / (4 * uses * uses)) ** 0.5
    return (centre + sign * margin) / (1 + z * z / uses)


def card_performance(battlelog):
    """PlayerBattleStats と同じ方法で、得意・苦手カードを [(カードID, 勝数, 対戦数), ...] の組で返す。

    2026-10-01から、得意は本人の全体の勝率より高いカード、苦手は低いカードだけから選ぶ。
    """
    tallies = {}
    battles = counted(battlelog)
    total = len(battles)
    wins = sum(result(b) == "W" for b in battles)
    for b in battles:
        won = result(b) == "W"
        for opponent in b["opponent"]:
            for card in opponent.get("cards") or []:
                t = tallies.setdefault(card["name"], [card["id"], 0, 0])
                t[1] += won
                t[2] += 1
    ranked = [tuple(t) for t in tallies.values() if t[2] >= 5]
    size = min(3, len(ranked) // 2)
    above = [t for t in ranked if t[1] * total > wins * t[2]]
    below = [t for t in ranked if t[1] * total < wins * t[2]]
    favorite = sorted(above, key=lambda t: (-_wilson(t[1], t[2], -1), -t[2]))[:size]
    weak = sorted(below, key=lambda t: (_wilson(t[1], t[2], 1), -t[2]))[:size]
    return favorite, weak


def win_rate(wins, uses):
    """Java の Math.round と同じく0.5は切り上げる。"""
    return int(100 * wins / uses + 0.5)


def streak(results):
    """新しい順の勝敗の並びから、先頭の連勝(正)・連敗(負)を数える。引き分けで止める。"""
    if not results or results[0] == "D":
        return 0
    n = 0
    for r in results:
        if r != results[0]:
            break
        n += 1
    return n if results[0] == "W" else -n
