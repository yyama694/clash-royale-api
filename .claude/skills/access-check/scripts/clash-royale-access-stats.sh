#!/bin/bash
# 本番VMの日次アクセス集計。VMの/usr/local/bin/clash-royale-access-stats.shに置き、cron(毎日01:00 UTC=日本時間10:00)で
# 前日(UTC)分を処理する。このファイルが正本で、直したらVMへ置き直す(運用.mdの「アクセス数の確認」)。
# - 件数(UAで既知のボットだけを除いた簡易な数え方)をdaily-access-stats.logに1行足す
# - その日のビーコンの行をbeacons/beacon-YYYY-MM-DD.logに残す。access-checkの「再訪」は前日以前の来訪をここから読む
#
# 引数で対象日(YYYY-MM-DD, UTC)を指定できる(cronが失敗した日のやり直し用。件数の行はもう1行足される)。
set -euo pipefail
export LC_ALL=C

# CPUが1つしかないので、アプリより後回しにする。
renice -n 19 -p $$ > /dev/null
ionice -c 2 -n 7 -p $$ 2> /dev/null || true

LOG_DIR=/var/log/httpd
OUT_DIR=/var/log/clash-royale-api
STATS_FILE=$OUT_DIR/daily-access-stats.log
BEACON_DIR=$OUT_DIR/beacons
# アクセスログ(毎週切り替えて4世代残す)と同じ約5週間。プライバシーポリシーの「約5週間で消去」はこれも含む。
BEACON_KEEP_DAYS=35

if [ "${1:-}" != "" ]; then
    TARGET_DATE_ISO="$1"
else
    TARGET_DATE_ISO=$(date -u -d 'yesterday' +%Y-%m-%d)
fi
TARGET_DATE_APACHE=$(date -u -d "$TARGET_DATE_ISO" +%d/%b/%Y)
TARGET_START_EPOCH=$(date -u -d "$TARGET_DATE_ISO" +%s)

# 既知のクローラー・スキャナー・CLIツールのUser-Agent(access-checkのcheck-access.shと同じ)
BOT_REGEX='GPTBot|ClaudeBot|Googlebot|GoogleOther|Google-Extended|bingbot|YandexBot|Baiduspider|DuckDuckBot|facebookexternalhit|Applebot|PetalBot|MJ12bot|AhrefsBot|SemrushBot|DotBot|SeznamBot|Bytespider|CCBot|meta-externalagent|curl/|python-requests|Go-http-client|l9scan|masscan|libredtail|Zgrab|libwww-perl|Wget/|okhttp|Scrapy|[Bb]ot[/ .]|/bot|[Ss]pider|[Cc]rawl'

# その日の行があるのは、その日の0時より後に更新されたファイルだけ(切り替え済みのファイルの更新時刻は切り替えた時刻)。
# 以前は全世代(.gzを含む約5週間分)を毎日展開し、1日分をシェルの変数に溜めていたため、ボットが増えた11月には
# CPUを30〜80分使い切り、メモリも約1.8GB使っていた(2026-11-16)。
mapfile -t LOG_FILES < <(find "$LOG_DIR" -maxdepth 1 -name 'access_log*' -newermt "@$TARGET_START_EPOCH" | sort)

# ボットのリクエストでNULなどが混ざると、grepはそれ以降の行を出さなくなるので-aを付ける(ハマりどころ.md)。
# 圧縮していないファイルはgrepに直接読ませる(zcat -fに通すと、数GBの全体がパイプを通る)。
collect() {
    local f
    for f in "${LOG_FILES[@]}"; do
        case "$f" in
            *.gz) zcat "$f" | grep -a -F "[$TARGET_DATE_APACHE:" || true ;;
            *)    grep -a -F "[$TARGET_DATE_APACHE:" "$f" || true ;;
        esac
    done
}

mkdir -p -m 700 "$BEACON_DIR"
BEACON_FILE=$BEACON_DIR/beacon-$TARGET_DATE_ISO.log
COUNTS=$(collect \
  | awk -v bot="$BOT_REGEX" -v beacon_out="$BEACON_FILE.tmp" '
    BEGIN { bot = tolower(bot); printf "" > beacon_out }
    { total++ }
    index($0, "\"POST /beacon") { print > beacon_out }
    tolower($0) !~ bot { real++; if (!($1 in ips)) { ips[$1]; uniq++ } }
    END { printf "total=%d real_requests=%d real_unique_ips=%d", total, real, uniq }')
mv "$BEACON_FILE.tmp" "$BEACON_FILE"

echo "${TARGET_DATE_ISO} ${COUNTS}" >> "$STATS_FILE"

CUTOFF=$(date -u -d "-$BEACON_KEEP_DAYS days" +%Y-%m-%d)
for f in "$BEACON_DIR"/beacon-*.log; do
    [ -e "$f" ] || continue
    d=${f##*/beacon-}
    d=${d%.log}
    if [[ "$d" < "$CUTOFF" ]]; then rm -f "$f"; fi
done
