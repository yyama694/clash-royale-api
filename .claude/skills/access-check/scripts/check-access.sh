#!/bin/bash
# アクセス状況を確認する(access-checkスキル本体)。
# 本番VMにSSHし、日次集計スクリプトを再実行したうえで、
# 既知のスキャナー・ユーザー本人IP(references/known_noise.md参照)も除外した数字を出す。
set -euo pipefail

SSH_KEY="C:\\Users\\Norio Fukuchi\\.ssh\\oci_clash_royale_api"
HOST="opc@161.33.136.175"
DATE="${1:-$(date -u +%Y-%m-%d)}"
APACHE_DATE=$(date -u -d "$DATE" +%d/%b/%Y)

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

ssh -i "$SSH_KEY" -o ConnectTimeout=10 "$HOST" bash -s -- "$DATE" "$APACHE_DATE" "$NOISE_IPS_B64" <<'REMOTE'
set -euo pipefail
DATE="$1"; APACHE_DATE="$2"; NOISE_REGEX=$(echo "$3" | base64 -d)

sudo /usr/local/bin/clash-royale-access-stats.sh "$DATE" >/dev/null
echo "== 日次集計(UAベースの簡易ボット除外のみ) =="
sudo grep "^$DATE " /var/log/clash-royale-api/daily-access-stats.log | tail -1
echo "(現在時刻 UTC: $(date -u '+%Y-%m-%d %H:%M'))"

BOT_REGEX='GPTBot|ClaudeBot|Googlebot|GoogleOther|Google-Extended|bingbot|YandexBot|Baiduspider|DuckDuckBot|facebookexternalhit|Applebot|PetalBot|MJ12bot|AhrefsBot|SemrushBot|DotBot|SeznamBot|Bytespider|CCBot|meta-externalagent|curl/|python-requests|Go-http-client|l9scan|masscan|Zgrab|libwww-perl|Wget/|okhttp|Scrapy|[Bb]ot[/ .]|/bot|[Ss]pider|[Cc]rawl'

sudo grep "\[$APACHE_DATE" /var/log/httpd/access_log \
  | grep -viE "$BOT_REGEX" \
  | grep -vE "^($NOISE_REGEX) " > /tmp/access-check-clean.log || true

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
# リファラが付かない流入でもこれで数えられる。
echo
echo "== 流入元タグ(?from=)付きのアクセス =="
grep -oE '\?from=[A-Za-z0-9_-]+' /tmp/access-check-clean.log | sort | uniq -c | sort -rn || echo "(なし)"
REMOTE
