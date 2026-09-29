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
FRIENDLY_TYPES = {"clanMate", "friendly"}


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
