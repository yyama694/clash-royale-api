#!/bin/bash
# X(t.co)からページを開いた訪問ごとに、同じIPから120秒以内にビーコンが届いたかを突き合わせ、
# ビーコンの取りこぼしを数える(2026-09-30、進捗ログのフェーズ1.101)。
# 送信のタイミングをloadからDOMContentLoadedに早めた効果を、変更前(9/22〜9/30、iPhoneのXアプリで
# 27人中6人が取りこぼし)と比べるために作った。
# 使い方: beacon-miss-check.sh [開始(UTC) 例: "2026-09-30 12:00"] [終了(UTC)]
set -euo pipefail

SSH_KEY="C:\\Users\\Norio Fukuchi\\.ssh\\oci_clash_royale_api"
HOST="opc@161.33.136.175"
START_EPOCH=$(date -u -d "${1:-2026-09-22 00:00}" +%s)
END_EPOCH=$(date -u -d "${2:-now}" +%s)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NOISE_FILE="$SCRIPT_DIR/../references/known_noise.md"
# 空白や`|`をsshの引数でそのまま渡すと誤解釈されるため、check-access.shと同じくbase64で渡す。
NOISE_IPS_B64=$(grep -oE '^\- `[0-9.]+`' "$NOISE_FILE" | grep -oE '[0-9.]+' | paste -sd '|' - | base64 -w0)

ssh -i "$SSH_KEY" -o ConnectTimeout=10 "$HOST" bash -s -- "$START_EPOCH" "$END_EPOCH" "$NOISE_IPS_B64" <<'REMOTE'
set -euo pipefail
START_EPOCH="$1"; END_EPOCH="$2"; NOISE_REGEX=$(echo "$3" | base64 -d)
echo "対象: UTC $(date -u -d "@$START_EPOCH" '+%Y-%m-%d %H:%M') 〜 $(date -u -d "@$END_EPOCH" '+%Y-%m-%d %H:%M')"

mapfile -t LOG_FILES < <(sudo find /var/log/httpd -maxdepth 1 -name 'access_log-*' -newermt "@$START_EPOCH" | sort)
LOG_FILES+=(/var/log/httpd/access_log)

sudo zcat -f "${LOG_FILES[@]}" \
  | grep -E '"GET /(player|clan|card|cards|ranking|favorites|decks|)[/? ].*"https://t\.co/|"POST /beacon' \
  | { grep -vE "^($NOISE_REGEX) " || true; } \
  | TZ=UTC awk -v start="$START_EPOCH" -v end="$END_EPOCH" '
BEGIN {
    split("Jan Feb Mar Apr May Jun Jul Aug Sep Oct Nov Dec", mm, " ");
    for (i = 1; i <= 12; i++) mon[mm[i]] = i;
}
{
    match($0, /\[([0-9]{2})\/([A-Za-z]{3})\/([0-9]{4}):([0-9]{2}):([0-9]{2}):([0-9]{2})/, t);
    ts = mktime(t[3] " " mon[t[2]] " " t[1] " " t[4] " " t[5] " " t[6]);
    if (ts < start || ts > end + 120) next;
    ip = $1;
    if ($0 ~ /"POST \/beacon/) { nb[ip]++; bts[ip, nb[ip]] = ts; next; }
    if (ts > end) next;
    n++; vip[n] = ip; vts[n] = ts; vpath[n] = $7;
    ua = $0; sub(/.*" "/, "", ua); sub(/"$/, "", ua);
    # PCはXのリンク確認(OVH等のサーバーがt.coのリファラを付けて来る)が大半なので、スマホと分けて見る。
    if (ua ~ /Twitter for iPhone/) kind = "iPhoneのXアプリ";
    else if (ua ~ /TwitterAndroid/) kind = "AndroidのXアプリ";
    else if (ua ~ /iPhone|iPad/) kind = "iPhoneのブラウザ";
    else if (ua ~ /Android/) kind = "Androidのブラウザ";
    else kind = "PC・その他";
    vkind[n] = kind;
    vtime[n] = t[1] "/" t[2] " " t[4] ":" t[5] ":" t[6];
}
END {
    for (i = 1; i <= n; i++) {
        hit = 0;
        for (j = 1; j <= nb[vip[i]]; j++) {
            d = bts[vip[i], j] - vts[i];
            if (d >= 0 && d <= 120) { hit = 1; break; }
        }
        key = vkind[i] SUBSEP vip[i];
        if (!(key in seen)) { seen[key] = 1; people[vkind[i]]++; }
        if (hit) got[key] = 1;
        if (!hit && vkind[i] != "PC・その他") printf "ビーコンなし  %-16s %-15s %s UTC  %s\n", vkind[i], vip[i], vtime[i], vpath[i] > "/dev/stderr";
    }
    for (key in seen) { split(key, k, SUBSEP); if (!(key in got)) missed[k[1]]++; }
    print "";
    print "== 種類別(IP単位。そのIPの訪問すべてでビーコンが来なかったら取りこぼし) ==";
    for (kd in people) printf "%-16s %3d人中 %3d人が取りこぼし\n", kd, people[kd], missed[kd] + 0;
}' 2>&1
REMOTE
