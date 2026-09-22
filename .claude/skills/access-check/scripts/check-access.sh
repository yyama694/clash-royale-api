#!/bin/bash
# アクセス状況を確認する(access-checkスキル本体)。
# 本番VMにSSHし、日本時間(JST)の暦日を対象にアクセスログを集計する。
# Apacheのログ自体はUTC基準のため、JSTの1日はUTC2日にまたがる(前日15:00〜当日15:00)。
# そのため単純な「UTC日付でgrep」ではJSTの実態とずれる。ここではJST日付から
# UTCの開始・終了epoch秒を計算し、リモート側でその範囲だけを集計する。
# 既知のスキャナー・ユーザー本人IP(references/known_noise.md参照)も除外する。
set -euo pipefail

SSH_KEY="C:\\Users\\Norio Fukuchi\\.ssh\\oci_clash_royale_api"
HOST="opc@161.33.136.175"
JST_DATE="${1:-$(TZ=Asia/Tokyo date +%Y-%m-%d)}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NOISE_FILE="$SCRIPT_DIR/../references/known_noise.md"

# known_noise.mdの`- \`IP\``行からIP一覧を拾う。ここに追記するだけで除外対象が増える。
# sshはリモートに渡す複数引数を1本の文字列に連結してリモートのシェルへ再度渡すため、
# 空白や`|`を含む値をそのまま引数で渡すと引数の境目やパイプ演算子として誤解釈される。
# base64にしてから渡し、リモート側でデコードすることでこれを避ける。
NOISE_IPS_B64=$(grep -oE '^\- `[0-9.]+`' "$NOISE_FILE" | grep -oE '[0-9.]+' | paste -sd '|' - | base64 -w0)
if [ -z "$NOISE_IPS_B64" ]; then
    NOISE_IPS_B64=$(printf '0.0.0.0' | base64 -w0)
fi

# JST日付の00:00:00〜翌日00:00:00(現在時刻がその前ならそこまで)をUTC epoch秒に変換する。
START_EPOCH=$(TZ=Asia/Tokyo date -d "$JST_DATE 00:00:00" +%s)
END_EPOCH_FULLDAY=$(TZ=Asia/Tokyo date -d "$JST_DATE 00:00:00 +1 day" +%s)
NOW_EPOCH=$(date -u +%s)
if [ "$END_EPOCH_FULLDAY" -gt "$NOW_EPOCH" ]; then
    END_EPOCH=$NOW_EPOCH
else
    END_EPOCH=$END_EPOCH_FULLDAY
fi

ssh -i "$SSH_KEY" -o ConnectTimeout=10 "$HOST" bash -s -- "$JST_DATE" "$START_EPOCH" "$END_EPOCH" "$NOISE_IPS_B64" <<'REMOTE'
set -euo pipefail
JST_DATE="$1"; START_EPOCH="$2"; END_EPOCH="$3"; NOISE_REGEX=$(echo "$4" | base64 -d)

# Apacheログの"dd/Mon/yyyy:HH:MM:SS"を年月日時分秒の数値(YYYYMMDDHHMMSS)に変換して
# 比較する。月ごとに1回だけdateを呼べば済むよう、範囲の開始・終了だけ変換しておく。
START_KEY=$(date -u -d "@$START_EPOCH" +%Y%m%d%H%M%S)
END_KEY=$(date -u -d "@$END_EPOCH" +%Y%m%d%H%M%S)

echo "対象範囲: 日本時間 $JST_DATE 00:00:00 〜 $(TZ=Asia/Tokyo date -d "@$END_EPOCH" '+%Y-%m-%d %H:%M:%S')"
echo "(UTC換算: $(date -u -d "@$START_EPOCH" '+%Y-%m-%d %H:%M:%S') 〜 $(date -u -d "@$END_EPOCH" '+%Y-%m-%d %H:%M:%S'))"

BOT_REGEX='GPTBot|ClaudeBot|Googlebot|GoogleOther|Google-Extended|bingbot|YandexBot|Baiduspider|DuckDuckBot|facebookexternalhit|Applebot|PetalBot|MJ12bot|AhrefsBot|SemrushBot|DotBot|SeznamBot|Bytespider|CCBot|meta-externalagent|curl/|python-requests|Go-http-client|l9scan|masscan|Zgrab|libwww-perl|Wget/|okhttp|Scrapy|[Bb]ot[/ .]|/bot|[Ss]pider|[Cc]rawl'

# ログ全体を1回のawkパスだけで対象範囲に絞る(1行ごとにdateを呼ぶと110000行規模で
# 実用的な速度が出ないため、月名→数値の変換だけをawk内テーブルで行う)。
sudo awk -v start="$START_KEY" -v end="$END_KEY" '
BEGIN {
    split("Jan Feb Mar Apr May Jun Jul Aug Sep Oct Nov Dec", months, " ");
    for (i = 1; i <= 12; i++) monnum[months[i]] = i;
}
{
    if (match($0, /\[([0-9]{2})\/([A-Za-z]{3})\/([0-9]{4}):([0-9]{2}):([0-9]{2}):([0-9]{2})/, m)) {
        key = sprintf("%04d%02d%02d%02d%02d%02d", m[3], monnum[m[2]], m[1], m[4], m[5], m[6]);
        if (key >= start && key <= end) print;
    }
}' /var/log/httpd/access_log > /tmp/access-check-window.log
sudo chmod 644 /tmp/access-check-window.log

echo
echo "== 日次集計(UAベースの簡易ボット除外のみ) =="
grep -viE "$BOT_REGEX" /tmp/access-check-window.log > /tmp/access-check-uafilter.log || true
echo "real_requests=$(wc -l < /tmp/access-check-uafilter.log) real_unique_ips=$(awk '{print $1}' /tmp/access-check-uafilter.log | sort -u | wc -l)"

grep -vE "^($NOISE_REGEX) " /tmp/access-check-uafilter.log > /tmp/access-check-clean.log || true

echo
echo "== 既知のスキャナー・本人IPも除外した実アクセス =="
echo "件数: $(wc -l < /tmp/access-check-clean.log)"
echo "ユニークIP数: $(awk '{print $1}' /tmp/access-check-clean.log | sort -u | wc -l)"

echo
echo "== ページ別の内訳(上位20) =="
awk '{print $7}' /tmp/access-check-clean.log \
  | sed -E 's#/player/[^/? ]+#/player/{tag}#; s#/clan/[^/? ]+#/clan/{tag}#; s#/card/[^/? ]+#/card/{id}#; s#\?.*##' \
  | sort | uniq -c | sort -rn | head -20

echo
echo "== 深いページ(player/clan/cards/ranking/favorites/card)へのアクセス =="
grep -E ' /(player|clan|cards|ranking|favorites|card)' /tmp/access-check-clean.log \
  | awk '{print $1, $4, $7}' || echo "(なし)"

echo
echo "== X(Twitter)経由のリファラ =="
grep -iE 't\.co|x\.com' /tmp/access-check-clean.log || echo "(なし)"

# X等に出すURLには ?from=x を付けている(2026-09-21、フェーズ1.55)。
# リファラが付かない流入でもこれで数えられる。クエリの先頭(?from=)にも
# 2番目以降のパラメータ(&from=)にも付き得るため両方拾う。
echo
echo "== 流入元タグ(?from=)付きのアクセス =="
grep -oE '[?&]from=[A-Za-z0-9_-]+' /tmp/access-check-clean.log | sed -E 's/^&/?/' | sort | uniq -c | sort -rn || echo "(なし)"
REMOTE
