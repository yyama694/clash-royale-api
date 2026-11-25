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
# Git Bash(Windows)はtzdataを持たず`TZ=Asia/Tokyo`を黙ってUTC扱いにするため(日本時間0〜9時の分が
# 集計から漏れていた)、tzdata不要なPOSIX形式で書く。ローカル側で計算する箇所はすべてこれを使う。
JST_TZ="JST-9"
JST_DATE="${1:-$(TZ=$JST_TZ date +%Y-%m-%d)}"

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

# JST日付の00:00:00〜23:59:59(現在時刻がその前ならそこまで)をUTC epoch秒に変換する。
START_EPOCH=$(TZ=$JST_TZ date -d "$JST_DATE 00:00:00" +%s)
# `date -d "... 00:00:00 +1 day"`は"+1"を時差(UTC+1)と解釈し、終了が8時間遅れて
# 1日が32時間になっていた(2026-09-26に判明)。秒数で足す。末尾の-1は下の範囲判定が終端を含むため。
END_EPOCH_FULLDAY=$((START_EPOCH + 86400 - 1))
NOW_EPOCH=$(date -u +%s)
if [ "$END_EPOCH_FULLDAY" -gt "$NOW_EPOCH" ]; then
    END_EPOCH=$NOW_EPOCH
else
    END_EPOCH=$END_EPOCH_FULLDAY
fi

REMOTE_OUT=$(mktemp)
trap 'rm -f "$REMOTE_OUT"' EXIT
SSH_STATUS=0
ssh -i "$SSH_KEY" -o ConnectTimeout=10 "$HOST" bash -s -- "$JST_DATE" "$START_EPOCH" "$END_EPOCH" "$NOISE_IPS_B64" > "$REMOTE_OUT" <<'REMOTE' || SSH_STATUS=$?
set -euo pipefail
JST_DATE="$1"; START_EPOCH="$2"; END_EPOCH="$3"; NOISE_REGEX=$(echo "$4" | base64 -d)

# ボットのリクエストでログにNUL等の制御文字が混ざると、grepはそれ以降を「binary file matches」として
# 1行も出さなくなる(2026-11-13に11/11が12件になって判明)。ログを読むgrepはすべてテキストとして扱わせる。
grep() { command grep -a "$@"; }

# Apacheログの"dd/Mon/yyyy:HH:MM:SS"を年月日時分秒の数値(YYYYMMDDHHMMSS)に変換して
# 比較する。月ごとに1回だけdateを呼べば済むよう、範囲の開始・終了だけ変換しておく。
START_KEY=$(date -u -d "@$START_EPOCH" +%Y%m%d%H%M%S)
END_KEY=$(date -u -d "@$END_EPOCH" +%Y%m%d%H%M%S)

echo "対象範囲: 日本時間 $JST_DATE 00:00:00 〜 $(TZ=Asia/Tokyo date -d "@$END_EPOCH" '+%Y-%m-%d %H:%M:%S')"
echo "(UTC換算: $(date -u -d "@$START_EPOCH" '+%Y-%m-%d %H:%M:%S') 〜 $(date -u -d "@$END_EPOCH" '+%Y-%m-%d %H:%M:%S'))"

BOT_REGEX='GPTBot|ClaudeBot|Googlebot|GoogleOther|Google-Extended|bingbot|YandexBot|Baiduspider|DuckDuckBot|facebookexternalhit|Applebot|PetalBot|MJ12bot|AhrefsBot|SemrushBot|DotBot|SeznamBot|Bytespider|CCBot|meta-externalagent|curl/|python-requests|Go-http-client|l9scan|masscan|libredtail|Zgrab|libwww-perl|Wget/|okhttp|Scrapy|[Bb]ot[/ .]|/bot|[Ss]pider|[Cc]rawl'

# logrotateが週1回(日曜 00:00 UTC=日本時間 9:00)access_logをaccess_log-YYYYMMDDに切り替える。
# 現行のaccess_logだけ読むと切り替え前の分が抜けるため(2026-09-27に9/26が0件になって判明)、
# 範囲の開始より後に最終更新されたローテーション済みファイルも読む。/var/log/httpdは
# rootしか中を見られずglobが展開されないので、sudo findで探す。zcat -fは圧縮の有無どちらも読める。
mapfile -t LOG_FILES < <(sudo find /var/log/httpd -maxdepth 1 -name 'access_log-*' -newermt "@$START_EPOCH" | sort)
LOG_FILES+=(/var/log/httpd/access_log)
echo "読んだログ: ${LOG_FILES[*]##*/}"

# ローテーション済みファイルは1GBを超えるので、範囲にかかるUTC日付の文字列でまず粗く絞る
# (JSTの1日はUTCの2日にまたがる)。そのうえで1回のawkパスで秒単位の範囲に絞る
# (1行ごとにdateを呼ぶと実用的な速度が出ないため、月名→数値の変換だけをawk内テーブルで行う)。
DAY_FROM=$(LC_ALL=C date -u -d "@$START_EPOCH" +%d/%b/%Y)
DAY_TO=$(LC_ALL=C date -u -d "@$END_EPOCH" +%d/%b/%Y)
sudo zcat -f "${LOG_FILES[@]}" | { grep -F -e "[$DAY_FROM:" -e "[$DAY_TO:" || true; } | awk -v start="$START_KEY" -v end="$END_KEY" '
BEGIN {
    split("Jan Feb Mar Apr May Jun Jul Aug Sep Oct Nov Dec", months, " ");
    for (i = 1; i <= 12; i++) monnum[months[i]] = i;
}
{
    if (match($0, /\[([0-9]{2})\/([A-Za-z]{3})\/([0-9]{4}):([0-9]{2}):([0-9]{2}):([0-9]{2})/, m)) {
        key = sprintf("%04d%02d%02d%02d%02d%02d", m[3], monnum[m[2]], m[1], m[4], m[5], m[6]);
        if (key >= start && key <= end) print;
    }
}' > /tmp/access-check-window.log
sudo chmod 644 /tmp/access-check-window.log

echo
echo "== ボット込みの総リクエスト(参考) =="
echo "全リクエスト: $(wc -l < /tmp/access-check-window.log)件 / ユニーク$(awk '{print $1}' /tmp/access-check-window.log | sort -u | wc -l) IP"
echo "HTMLページの表示(GETで200、静的ファイル・beacon・robots.txt・sitemap除く): $(awk '$6 == "\"GET" && $9 == 200 {print $7}' /tmp/access-check-window.log | grep -vcE '^/(beacon|css/|js/|images/|favicon|manifest|robots\.txt|sitemap|apple-touch|llms\.txt)' || true)"
echo "-- ボット別のリクエスト数 --"
# BOT_REGEXの汎用パターン(Bot/・crawl等)は1つのUAに何度も当たるので、名前の付いたボットを先に1つだけ拾う。
awk -F'"' -v named='ClaudeBot|GPTBot|OAI-SearchBot|ChatGPT-User|Applebot|meta-externalagent|Amazonbot|Googlebot|GoogleOther|bingbot|PerplexityBot|Bytespider|YandexBot|AhrefsBot|SemrushBot|PetalBot|CCBot|ShapBot|Reflectionbot' -v bot="$BOT_REGEX" '
    match($6, named) { print substr($6, RSTART, RLENGTH); next }
    $6 ~ bot { print "その他のボット"; next }
    { print "ボット以外のUA" }' /tmp/access-check-window.log | sort | uniq -c | sort -rn | head -12
# 正体を隠したボット(MacのChromeを名乗る)には、Apacheが確かめ用のページ(約700バイト)を返す(2026-11-10、運用.md)。
# 効いているかは、差し替えた件数と、同じUAでアプリまで届いた件数、通信の版(本物のChromeはh2)で見る。
# 確かめ用のページは、11/10〜11/12夜はApacheが200・600〜900バイトで、それ以降はアプリ(HumanCheckInterceptor)が403で返す。
echo "-- 正体を隠したボット対策(ブラウザを名乗る/player/・/clan/のGET。スマホは通行証の対象外) --"
{ grep -E '"GET /(player|clan)/' /tmp/access-check-window.log | grep -viE "$BOT_REGEX" | awk -F'"' '{
        split($3, s, " "); split($2, r, " "); m = ($6 ~ /iPhone|iPad|Android/) ? "スマホ" : "スマホ以外";
        if (s[1] == 200 && s[2] >= 600 && s[2] <= 900) k = "Apacheの確かめ用のページ";
        else if (s[1] == 403) k = "アプリの確かめ用のページ";
        else k = "アプリの処理まで届いた";
        c[m " / " k]++; proto[r[3]]++ }
    END { for (x in c) printf "  %8d %s\n", c[x], x; for (p in proto) printf "  %8d (%s)\n", proto[p], p }'; } || true
# 10/27にrobots.txtで対戦詳細を止めた効果を見るため、Googlebotが何の画面を取っているかを出す(2026-11-12追加)。
echo "-- Googlebotが取った画面(UAがGooglebot、ページの種類別) --"
awk -F'"' '$6 ~ /Googlebot/ { split($2, r, " "); p = r[2]; sub(/\?.*/, "", p);
        if (p ~ /^\/player\/[^\/]+\/battles\//) t = "対戦詳細"; else if (p ~ /^\/player\//) t = "プレイヤー";
        else if (p ~ /^\/clan\//) t = "クラン"; else if (p ~ /^\/card\//) t = "カード詳細"; else if (p ~ /^\/cards/) t = "カード一覧";
        else if (p ~ /^\/decks/) t = "デッキ"; else if (p ~ /^\/ranking/) t = "ランキング"; else if (p ~ /^\/robots|^\/sitemap/) t = "robots.txt・sitemap";
        else if (p == "/") t = "トップ"; else t = "その他"; print t }' /tmp/access-check-window.log | sort | uniq -c | sort -rn
# 確かめ用のページをすり抜けている分や、UAを替えて来ている分を見分けるため、ブラウザを名乗るアクセス
# (BOT_REGEXに当たらないUA)を、UAの種類ごとに人らしさの手がかりと並べる(2026-11-12追加)。
# 人はCSSとビーコンを取りに来るので、CSS・ビーコンのIP数がIP数よりずっと少ない種類はボットが多い。
KIND_FN='
function kind(ua) {
    if (ua ~ /Edg\/|OPR\//) return "Edge・Opera";
    if (ua ~ /Macintosh; Intel Mac OS X 10_15_7.*Chrome\//) return "MacのChrome";
    if (ua ~ /Windows NT.*Chrome\//) return "WindowsのChrome";
    if (ua ~ /iPhone|iPad/) return "iPhone/iPad";
    if (ua ~ /Android/) return "Android";
    if (ua ~ /Macintosh/ && ua ~ /Safari/ && ua !~ /Chrome/) return "MacのSafari";
    if (ua ~ /Firefox/) return "Firefox";
    if (ua ~ /Linux.*Chrome\//) return "LinuxのChrome";
    return "その他";
}'
grep -viE "$BOT_REGEX" /tmp/access-check-window.log > /tmp/access-check-browser.log || true
echo "-- ブラウザを名乗るアクセス(UAの種類別。件数 / IP数 / うち/player/・/clan/ / Refererなし / HTTP/1.1 / CSSを取ったIP / ビーコンを送ったIP) --"
awk -F'"' "$KIND_FN"'
{
    split($1, a, " "); ip = a[1]; split($2, r, " "); path = r[2]; k = kind($6);
    n[k]++; if (!((k, ip) in seen)) { seen[k, ip]; ips[k]++ }
    if (path ~ /^\/(player|clan)\//) pc[k]++;
    if ($4 == "-") noref[k]++;
    if (r[3] == "HTTP/1.1") h1[k]++;
    if (path ~ /^\/css\// && !((k, ip) in css)) { css[k, ip]; cssips[k]++ }
    if (path ~ /^\/beacon/ && !((k, ip) in bc)) { bc[k, ip]; bcips[k]++ }
}
END { for (k in n) printf "%9d %8d %9d %9d %9d %6d %6d  %s\n", n[k], ips[k], pc[k], noref[k], h1[k], cssips[k], bcips[k], k }' /tmp/access-check-browser.log | sort -rn
# 確かめ用のページの条件(UA・Referer)を決めるため、/player/・/clan/を、通信の版とRefererの送り元で分ける。
echo "-- ブラウザを名乗る/player/・/clan/のGET(UAの種類 × 通信の版 × Referer。上位15) --"
awk -F'"' "$KIND_FN"'
$2 ~ /^GET \/(player|clan)\// {
    split($2, r, " "); v = (r[3] == "HTTP/2.0") ? "HTTP/2" : r[3];
    ref = $4; if (ref == "-") rc = "Refererなし"; else if (ref ~ /^https?:\/\/princess-tower\.duckdns\.org\//) rc = "Referer=自サイト"; else rc = "Referer=外部";
    c[kind($6) " / " v " / " rc]++ }
END { for (k in c) printf "%9d  %s\n", c[k], k }' /tmp/access-check-browser.log | sort -rn | awk 'NR <= 15'
# 毎回同じ形のUAを数字だけ変えて名乗っているかを見る(数字をNにまとめた形の上位)。
echo "-- /player/・/clan/を取ったブラウザのUAの形(数字をNにまとめた上位8) --"
awk -F'"' '$2 ~ /^GET \/(player|clan)\// { ua = $6; gsub(/[0-9]+/, "N", ua); c[substr(ua, 1, 140)]++ }
    END { for (k in c) printf "%9d  %s\n", c[k], k }' /tmp/access-check-browser.log | sort -rn | awk 'NR <= 8'
# 確かめ用のページの後に同じIPが同じURLを読み直していれば、JSを動かした(人か、JSを動かすボット)。
# Refererを付けて来たものは確かめ用のページの対象外なので、Refererの送り元も出す。
echo "-- MacのChromeを名乗る/player/・/clan/のGETのうち、アプリまで届いたもの --"
grep -E '"GET /(player|clan)/' /tmp/access-check-window.log | awk -F'"' '
$6 ~ /Macintosh; Intel Mac OS X 10_15_7.*Chrome\// && $6 !~ /Edg\/|OPR\// {
    split($1, a, " "); ip = a[1]; split($2, r, " "); path = r[2]; split($3, s, " ");
    if (s[1] == 403 && s[2] >= 1300 && s[2] <= 2000) { ch[ip, path]; chip[ip]; next }
    app++; if (!(ip in appip)) { appip[ip]; appips++ }
    if ((ip, path) in ch) reload++; else if (ip in chip) otherurl++;
    ref = $4; if (ref == "-") rk = "Refererなし"; else { sub(/^https?:\/\//, "", ref); sub(/[\/?].*/, "", ref); rk = "Referer: " ref }
    refc[rk]++; proto[rk, r[3]]++; st[s[1]]++;
}
END {
    printf "  %d件 / %d IP。うち確かめ用のページの後に同じURLを読み直した: %d件、同じIPが別のURLで確かめ用のページを受けていた: %d件\n", app, appips, reload, otherurl;
    for (k in refc) printf "  %8d %s(HTTP/1.1 %d・HTTP/2 %d)\n", refc[k], k, proto[k, "HTTP/1.1"], proto[k, "HTTP/2.0"] | "sort -rn | head -8";
    close("sort -rn | head -8");
    printf "  ステータス:"; for (k in st) printf " %s=%d", k, st[k]; print "";
}'
echo "-- 確かめ用のページを受けた送信元(件数の多い/16。件数 / IP数) --"
awk -F'"' '$6 ~ /Macintosh; Intel Mac OS X 10_15_7.*Chrome\// && $2 ~ /^GET \/(player|clan)\// {
    split($3, s, " "); if (s[1] != 403 || s[2] < 1300 || s[2] > 2000) next;
    split($1, a, " "); ip = a[1];
    if (ip ~ /:/) { split(ip, o, ":"); p = o[1] ":" o[2] "::/32" } else { split(ip, o, "."); p = o[1] "." o[2] ".0.0/16" }
    c[p]++; if (!((p, ip) in u)) { u[p, ip]; ui[p]++ }; tot++; if (!(ip in all)) { all[ip]; alln++ }
}
END { printf "  合計 %d件 / %d IP\n", tot, alln; for (p in c) printf "  %8d %7d %s\n", c[p], ui[p], p | "sort -rn | head -10" }' /tmp/access-check-window.log
echo "-- VMの負荷(実行した時点) --"
uptime
vmstat 5 2 | tail -1 | awk '{print "CPU: user " $13 "% / system " $14 "% / idle " $15 "% / iowait " $16 "%(5秒間)"}'

# 害悪ボットの動きを毎回の報告に載せる(2026-11-25にユーザーが依頼)。11/24に手で数えた項目(1.258)をそのまま出す。
# 応答の大きさで見分ける: 確かめ用のページ(計算)は約1,380バイト、ボタンのページは1,949バイト(どちらもアプリの403)、
# ApacheのUA拒否(block-scrapers.conf。Lightpandaなど)は199バイトの403。ページを直して大きさが変わったらここも直す。
echo
echo "== 害悪ボットの動き =="
echo "-- 名前で見分けている害悪ボット(件数 / IP数 / うち403) --"
awk -F'"' 'match($6, /Lightpanda|WebSiphon|ClashGegnerfenster/) { k = substr($6, RSTART, RLENGTH); split($1, a, " "); split($3, s, " ");
        n[k]++; tot++; if (s[1] == 403) d[k]++; if (!((k, a[1]) in u)) { u[k, a[1]]; ip[k]++ } }
    # 1件も無い日に length(n) で数えると、gawkはnを配列でなく値として扱い、続く for (k in n) で止まる(11/23・11/24の集計が
    # 終了コード2で止まった)。件数は別の変数で数える。
    END { if (!tot) print "  (なし)"; for (k in n) printf "  %9d %8d %9d  %s\n", n[k], ip[k], d[k], k }' /tmp/access-check-window.log | sort -rn
echo "-- /player/・/clan/のGETへの応答(ブラウザを名乗るもの。名乗っているクローラーは確かめ用のページの対象外なので除く。件数 / IP数) --"
awk -F'"' '$2 ~ /^GET \/(player|clan)\// && $6 !~ /Lightpanda|WebSiphon|ClashGegnerfenster/ { split($3, s, " "); split($1, a, " ");
        if (s[1] == 403 && s[2] >= 1300 && s[2] <= 1500) k = "確かめ用のページ(計算)で止めた";
        else if (s[1] == 403 && s[2] >= 1900 && s[2] <= 2000) k = "ボタンのページで止めた";
        else if (s[1] == 403) k = "その他の403";
        else if (s[1] == 200) k = "アプリの画面を返した(200)";
        else k = "その他(" s[1] ")";
        n[k]++; if (!((k, a[1]) in u)) { u[k, a[1]]; ip[k]++ } }
    END { for (k in n) printf "  %9d %8d  %s\n", n[k], ip[k], k }' /tmp/access-check-browser.log | sort -rn
echo "-- 確かめ用のページ(計算)で止めた件数の時間別(日本時間) --"
awk -F'"' '$2 ~ /^GET \/(player|clan)\// { split($3, s, " "); if (s[1] != 403 || s[2] < 1300 || s[2] > 1500) next;
        if (match($1, /:([0-9]{2}):[0-9]{2}:[0-9]{2} /, m)) c[(m[1] + 9) % 24]++ }
    END { line = ""; for (h = 0; h < 24; h++) if (h in c) line = line sprintf(" %02d時=%d", h, c[h]); print " " line }' /tmp/access-check-browser.log
echo "-- ボタンの段: 計算を解いた(POST /human-checkで204。UAの種類別の件数 / IP数) --"
awk -F'"' "$KIND_FN"'
$2 ~ /^POST \/human-check HTTP/ { split($3, s, " "); if (s[1] != 204) next; split($1, a, " "); k = kind($6);
    n[k]++; tot++; if (!((k, a[1]) in u)) { u[k, a[1]]; ip[k]++ } if (!(a[1] in all)) { all[a[1]]; alln++ } }
END { printf "  合計 %d件 / %d IP\n", tot, alln; for (k in n) printf "  %9d %8d  %s\n", n[k], ip[k], k | "sort -rn"; close("sort -rn") }' /tmp/access-check-window.log
echo "-- ボタンの段: 押した(POST /human-check/pressで204。押すまでの秒数。人は1秒〜数十秒にばらける) --"
awk -F'"' "$KIND_FN"'
$2 ~ /^POST \/human-check\/press\?d=/ { split($3, s, " "); if (s[1] != 204) next; split($1, a, " "); split($2, r, " ");
    dv = r[2]; sub(/.*[?&]d=/, "", dv); sub(/&.*/, "", dv);
    tot++; if (!(a[1] in all)) { all[a[1]]; alln++ } list = list sprintf(" %.1f秒(%s)", dv / 1000, kind($6)) }
END { printf "  %d件 / %d IP\n", tot, alln; if (tot > 0 && tot <= 30) print " " list; else if (tot > 30) print "  (30件を超えたので明細は省略。自動で押されている疑い)" }' /tmp/access-check-window.log
# CPUは1分ごとのsarの記録から、範囲の平均と張り付いていた分数を出す(実行した時点の値だけでは1日の様子が分からないため)。
echo "-- VMのCPU(sarの1分ごとの記録。範囲の平均) --"
SAR_FROM_DAY=$(date -u -d "@$START_EPOCH" +%Y%m%d); SAR_TO_DAY=$(date -u -d "@$END_EPOCH" +%Y%m%d)
{
    for day in $(printf '%s\n' "$SAR_FROM_DAY" "$SAR_TO_DAY" | sort -u); do
        f=/var/log/sa/sa$day; [ -f "$f" ] || continue
        s=00:00:00; e=23:59:59
        [ "$day" = "$SAR_FROM_DAY" ] && s=$(date -u -d "@$START_EPOCH" +%H:%M:%S)
        [ "$day" = "$SAR_TO_DAY" ] && e=$(date -u -d "@$END_EPOCH" +%H:%M:%S)
        LC_ALL=C sar -u -f "$f" -s "$s" -e "$e" 2>/dev/null || true
    done
} | awk '$1 ~ /^[0-9][0-9]:/ && $2 == "all" { n++; idle += $NF; if ($NF < 5) full++ }
    END { if (n) printf "  使用率の平均 %.0f%%(%d分の記録)。空きが5%%未満だった分: %d分\n", 100 - idle / n, n, full; else print "  (記録なし)" }'

echo
echo "== 日次集計(UAベースの簡易ボット除外のみ) =="
grep -viE "$BOT_REGEX" /tmp/access-check-window.log > /tmp/access-check-uafilter.log || true
echo "real_requests=$(wc -l < /tmp/access-check-uafilter.log) real_unique_ips=$(awk '{print $1}' /tmp/access-check-uafilter.log | sort -u | wc -l)"

grep -vE "^($NOISE_REGEX) " /tmp/access-check-uafilter.log > /tmp/access-check-clean.log || true

echo
echo "== 既知のスキャナー・本人IPも除外した実アクセス =="
echo "件数: $(wc -l < /tmp/access-check-clean.log)"
echo "ユニークIP数: $(awk '{print $1}' /tmp/access-check-clean.log | sort -u | wc -l)"


# ビーコン(/beacon)はブラウザがJSを実行したときだけ叩かれる(2026-09-22追加、フェーズ1.61)。
# HTMLを取得するだけのクローラーはここに現れないため、ログのUA/IPによる推定と違って
# 「本当にブラウザで表示された回数」を直接数えられる。
# ユーザー本人の開発用IPや既知スキャナーの分まで数えると意味が無いので、ここでも
# known_noise.md のIPは落とす(ビーコン自体はJSを実行した本物のブラウザからしか来ないが、
# ユーザー本人の動作確認アクセスは「実訪問者」ではないため)。
# sendBeaconは必ずPOSTで送る。GETはクローラーがJS内のURLを拾って叩いたもの(GoogleOtherで実例あり)。
# JSを実行して描画するクローラーもビーコンを送るので、ページの集計と同じBOT_REGEXでも落とす
# (2026-09-30に、Metaのmeta-externalagentが/card/{id}を描画して、除外後12件のうち11件を占めていた)。
grep '"POST /beacon' /tmp/access-check-window.log > /tmp/access-check-beacon-all.log || true
grep -viE "$BOT_REGEX" /tmp/access-check-beacon-all.log | grep -vE "^($NOISE_REGEX) " > /tmp/access-check-beacon.log || true
# クイズは1画面の中で進むので、操作(e=start・finish・practice・practice-finish・share、n=問題数)も/beaconに送る(2026-11-07追加)。
# ページ表示ではないので、表示回数(PV)の集計からは外して別に数える。
grep -E '[?&]e=' /tmp/access-check-beacon.log > /tmp/access-check-quiz-events.log || true
grep -vE '[?&]e=' /tmp/access-check-beacon.log > /tmp/access-check-beacon-pages.log || true
mv /tmp/access-check-beacon-pages.log /tmp/access-check-beacon.log
# 計算を解くボット(2026-11-13 17時から)は、確かめ用のページの計算を解いてビーコンまで送る。UAは「X11; Linux x86_64」の
# Chromeで、住宅・携帯回線のIPを数ページごとに替えるため、逆引きやデータセンター判定では外れない。
# このUAのビーコンを、実訪問・PV・画面別から外して別に数える(人のLinuxのパソコンは1日0〜1人なので巻き込みは小さい)。
# 外さないと11/13は実訪問が9千人超になり、逆引きにも時間がかかった。
# 最初は「その日にPOST /human-checkをした送り元」で外したが、同じボットは確かめ用のページの無いカード詳細なども巡回し、
# そのIPが/human-checkを送るのは日付が変わった後のこともあって約45 IPが漏れたので、ビーコンのUAで外す(2026-11-16)。
# 外すのはボタンの段を入れた2026-11-17 07:29 UTC(日本時間 16:29)より前のビーコンだけ(2026-11-25)。ボタンの段からは
# 解くボットは押さずに止まり(進捗ログ1.256)、11/19に解いていたのはLightpandaだった(Apacheで拒否済み)ので、
# LinuxのChromeのビーコンは人のもの(11/19 14:43 UTCのAT&Tの人など)。UAで外し続けると人を落とす。
SOLVER_FN='function solver(   m, mon) {
    if ($6 !~ /X11; Linux x86_64\)/ || $6 !~ /Chrome\//) return 0;
    if (!match($1, /\[([0-9]{2})\/([A-Za-z]{3})\/([0-9]{4}):([0-9]{2}):([0-9]{2}):([0-9]{2})/, m)) return 0;
    mon = (index("JanFebMarAprMayJunJulAugSepOctNovDec", m[2]) + 2) / 3;
    return sprintf("%04d%02d%02d%02d%02d%02d", m[3], mon, m[1], m[4], m[5], m[6]) < "20261117072900";
}'
awk -F'"' "$SOLVER_FN"'
solver() && $2 ~ /^POST \/human-check/ {split($1, a, " "); print a[1]}' \
    /tmp/access-check-window.log | sort -u > /tmp/access-check-solver-ips.txt
awk -F'"' "$SOLVER_FN"' solver()' /tmp/access-check-beacon.log > /tmp/access-check-solver-beacon.log || true
awk -F'"' "$SOLVER_FN"' !solver()' /tmp/access-check-beacon.log > /tmp/access-check-beacon-human.log || true
mv /tmp/access-check-beacon-human.log /tmp/access-check-beacon.log
awk -F'"' "$SOLVER_FN"' !solver()' /tmp/access-check-quiz-events.log > /tmp/access-check-quiz-human.log || true
mv /tmp/access-check-quiz-human.log /tmp/access-check-quiz-events.log

# 検索エンジンから来た訪問(2026-10-04追加、TODO.mdの「ページビュー向上」12)。検索対策の効果はこれで測る。
# リファラが検索エンジンのページ表示を拾い、同じIPからビーコンが来ていれば「実訪問」とする。
# Googleの先読み(逆引きがgooglezip・google-proxy)はHTMLだけ取りに来る。本人が実際に開いたときの
# ビーコンは本人の回線のIPから来るため、ここでは結び付けられず「ビーコンなし」になる。
# search.google.com はSearch Console(管理者の画面)なので数えない。
# awkの-vはバックスラッシュを解釈してしまうので、正規表現は環境変数で渡す。
SEARCH_REF_REGEX='^(https?://([a-z0-9-]+\.)*(google|bing|duckduckgo|yahoo|yandex|ecosia|naver|baidu|qwant|seznam|startpage)\.[a-z.]+(/|$)|https?://search\.brave\.com/|android-app://com\.google\.android\.googlequicksearchbox)'
SEARCH_RE="$SEARCH_REF_REGEX" awk -F'"' '
{
    split($1, a, " "); split($2, r, " "); path = r[2]; ref = $4;
    if (path ~ /^\/beacon/ || path ~ /\.(css|js|png|ico|jpg|jpeg|webp|svg|txt|xml|html)(\?|$)/) next;
    if (ref ~ /^https?:\/\/search\.google\.com\//) next;
    if (ref !~ ENVIRON["SEARCH_RE"]) next;
    host = ref; sub(/^[a-z-]+:\/\//, "", host); sub(/\/.*/, "", host);
    print a[1], substr(a[4], 2), path, host;
}' /tmp/access-check-clean.log > /tmp/access-check-search.log || true
awk '{print $1}' /tmp/access-check-beacon.log | sort -u > /tmp/access-check-beacon-ips.txt

# データセンター由来の切り分け。IPのCIDR一覧を手で持つと保守しきれないため、
# 逆引き(rDNS)のホスト名でホスティング事業者を判定する。事業者は自分のIPにその事業者と
# 分かるホスト名を付けているので、CIDRを追いかけるより変化に強い。
# 引くのはビーコンの送信元と検索エンジンから来たIPだけ。以前はUAで除いた後の全IPを1件ずつ引いて
# 「人間候補」を出していたが、住宅プロキシ経由のクローラーが大半で人の数を表さず、
# 4,000IPを引くのに30分以上かかっていた(2026-10-23にやめた)。並列で引き、結果は /tmp/access-check-rdns.txt に持つ。
cat /tmp/access-check-beacon-ips.txt <(awk '{print $1}' /tmp/access-check-search.log) | sort -u > /tmp/access-check-ips.txt
# 引けなかったときdigは ";; connection timed out..." のような文を返す。ホスト名の形を
# していないものは「逆引きなし」として扱う。x.y.z.in-addr.arpa. は事業者の手がかりが
# 無い(PTRを形式的に置いているだけ)ので、同じく逆引きなし扱いにする。
# digが無い環境ではgetentで引く(引けないとき非0で終わるので握りつぶす)。
xargs -r -P 16 -n 1 bash -c '
    name=$( { if command -v dig >/dev/null 2>&1; then dig +short +time=2 +tries=1 -x "$1" 2>/dev/null; else getent hosts "$1" 2>/dev/null | awk "{print \$2}"; fi; } | head -1)
    case "$name" in *" "*|""|*in-addr.arpa.) name="-" ;; esac
    printf "%s\t%s\n" "$1" "$name"' _ < /tmp/access-check-ips.txt > /tmp/access-check-rdns.txt
rdns_of() { grep -m1 -P "^$1\t" /tmp/access-check-rdns.txt | cut -f2; }

# 逆引きが引けない送信元は約4割あり、その大半は一般の携帯・固定回線だった(T-Mobile・Jio・ソフトバンクなど。
# 2026-11-25に11/18〜11/25の送信元で確かめた)。逆引きだけではデータセンターと見分けられないので、回線の事業者(AS)も引く。
# 表はiptoasn.comの公開データ(IPの範囲→AS番号・事業者名)をVMに置いて引くので、訪問者のIPを外に送らない。
# 7日ごとに取り直し、取れなければ事業者なしで続ける。
ASN_DB=/tmp/access-check-ip2asn-v4.tsv
if [ ! -s "$ASN_DB" ] || [ -n "$(find "$ASN_DB" -mtime +7 2> /dev/null)" ]; then
    curl -fsS --max-time 60 https://iptoasn.com/data/ip2asn-v4.tsv.gz 2> /dev/null | gunzip -c > "$ASN_DB.tmp" 2> /dev/null \
        && [ -s "$ASN_DB.tmp" ] && mv "$ASN_DB.tmp" "$ASN_DB" || rm -f "$ASN_DB.tmp"
fi
: > /tmp/access-check-asn.txt
if [ -s "$ASN_DB" ]; then
    awk -F'\t' '
    function num(ip,   o) { if (split(ip, o, ".") != 4) return -1; return ((o[1] * 256 + o[2]) * 256 + o[3]) * 256 + o[4] }
    FNR == NR { if ($3 == 0) next; n++; lo[n] = num($1); hi[n] = num($2); as[n] = $3; desc[n] = $5; next }
    {
        x = num($1); r = "-";
        if (x >= 0) {
            a = 1; b = n;
            while (a <= b) { m = int((a + b) / 2); if (lo[m] <= x) { if (x <= hi[m]) { r = "AS" as[m] " " desc[m]; break } a = m + 1 } else b = m - 1 }
        }
        printf "%s\t%s\n", $1, r;
    }' "$ASN_DB" /tmp/access-check-ips.txt > /tmp/access-check-asn.txt
else
    echo "(回線の事業者の表(iptoasn.com)を取れなかったので、事業者での判定はしていない)"
fi
asn_of() { grep -m1 -P "^$1\t" /tmp/access-check-asn.txt | cut -f2; }

# ホスティング事業者・クラウドを示すホスト名の断片。当たった=人間の家庭回線ではない。
# `v22025112337...` のような数字だけの長いホスト名はVPSの自動採番(Contabo系)の典型。
DC_REGEX='ovh\.|ovh\.net|your-server\.de|hetzner|amazonaws|digitalocean|linode|vultr|choopa|contabo|scaleway|leaseweb|m247|oraclecloud|azure|googleusercontent|hostwinds|colocrossing|datacenter|\.cloud\.|vps|dedicated|servers?\.|srv[0-9]*\.[a-z]+$|srv\.(de|net|com)|\bv[0-9]{8,}\.|p14\.io|hwclouds|pfcloud'
# 素性を名乗っている大手スキャナー・インターネット調査プロジェクト。逆引き名にそのまま出る。
# UAはブラウザを偽装していても逆引きは自分のドメインのままなので、ここで捕まえられる。
SCANNER_REGEX='googlebot|crawl-|censys|shodan|internet-measurement|internet-census|bufferover\.run|criminalip|deepfield|infrawat|no-reverse-dns-configured|rwth-aachen|uni-[a-z]+\.de|scan\.|\bscanner|netsystemsresearch|binaryedge|onyphe|driftnet|stretchoid|alphastrike|securitytrails|leakix|palo ?alto|expanse|group-ib'
grep -P "\t.*($DC_REGEX|$SCANNER_REGEX)" /tmp/access-check-rdns.txt | cut -f1 | sort -u > /tmp/access-check-dc-ips.txt || true
# 回線の事業者がホスティング・クラウドなら、逆引きが何であれデータセンター由来とみる(2026-11-25追加)。
# iCloudプライベートリレーの出口(Cloudflare・Fastly・Akamai)は人の閲覧なので入れない(Akamaiのうちlinodeはホスティング)。
# VPNの事業者のうち、ホスティング事業者の回線を使うもの(M247・Datacampなど)はここで外れる。
HOSTING_ASN_REGEX='\bAS15169 |amazon|google-cloud|microsoft|azure|\bovh\b|hetzner|digitalocean|linode|vultr|choopa|contabo|m247|datacamp|cdn77|hostroyale|hostkey|leaseweb|alibaba|aliyun|tencent|hwclouds|huawei cloud|oracle|scaleway|online s\.a\.s|ionos|hostinger|hostwinds|colocrossing|psychz|quadranet|zenlayer|g-core|gcore|selectel|timeweb|vdsina|aeza|kamatera|ucloud|bj-guanghuan|nwcd|worldstream|nforce|cloudzy|hosting|\bservers?\b|\bvps\b|data ?cent(er|re)'
grep -iP "\t.*($HOSTING_ASN_REGEX)" /tmp/access-check-asn.txt | cut -f1 | sort -u > /tmp/access-check-hosting-ips.txt || true
# 逆引きが引けないことは、もう機械の目印にしない(2026-11-25)。以前は「一般のISPはほぼ必ずPTRを設定している」として
# 検索経由の判定から外していたが、11/18〜11/25のビーコンの送信元では人の4割にPTRが無く、検索から来た人の約半分を落としていた。

# t.co経由を装う分散スキャナー群の目印(2026-09-22発見)。多数の別IPが揃って
# 同じ古いChromeのUAを名乗る。実在ユーザーの分布としては起こり得ない。
STALE_UA='Chrome/103\.0\.0\.0 Safari'
grep -E "$STALE_UA" /tmp/access-check-clean.log | awk '{print $1}' | sort -u > /tmp/access-check-staleua-ips.txt || true

cat /tmp/access-check-dc-ips.txt /tmp/access-check-hosting-ips.txt /tmp/access-check-staleua-ips.txt \
  | sort -u > /tmp/access-check-machine-ips.txt

echo
echo "== ページ別の内訳(除外前、参考、上位20) =="
awk '{print $7}' /tmp/access-check-clean.log \
  | sed -E 's#/player/[^/? ]+#/player/{tag}#; s#/clan/[^/? ]+#/clan/{tag}#; s#/card/[^/? ]+#/card/{id}#; s#\?.*##' \
  | sort | uniq -c | sort -rn | head -20 || true

echo
echo "== X(Twitter)経由のリファラ =="
grep -iE 't\.co|x\.com' /tmp/access-check-clean.log || echo "(なし)"

# X等に出すURLには ?from=x を付けている(2026-09-21、フェーズ1.55)。
# リファラが付かない流入でもこれで数えられる。クエリの先頭(?from=)にも
# 2番目以降のパラメータ(&from=)にも付き得るため両方拾う。
# リファラ欄やビーコンのURLにも同じfrom=が載るため、行全体から拾うと1回の訪問が5件前後に
# 膨らむ(2026-09-27に判明)。リクエストのURL($7)だけを、ビーコンを除いて数える。
# クローラーも?from=付きのURLを拾って叩くので、人の流入はビーコンの節の「流入元タグ別」で見る。
echo
echo "== 流入元タグ(?from=)付きのアクセス(除外前、参考) =="
awk '$7 !~ /^\/beacon/ {print $7}' /tmp/access-check-clean.log | grep -oE '[?&]from=[A-Za-z0-9_-]+' | sed -E 's/^&/?/' | sort | uniq -c | sort -rn || echo "(なし)"

echo
echo "== 実ブラウザ表示(ビーコン /beacon) =="
# 除外後が0件でも「仕組みが動いていない」のか「本人の分しか無かった」のかを見分けられるよう、除外前の件数も出す。
echo "除外前(ボットのUA・本人・既知ノイズ込み): $(wc -l < /tmp/access-check-beacon-all.log)件 / ユニーク$(awk '{print $1}' /tmp/access-check-beacon-all.log | sort -u | wc -l) IP"
echo "解くボット(11/17 16:29より前の、ビーコンのUAがLinuxのChromeのもの。下の件数・画面別・再訪から外した):ビーコン$(wc -l < /tmp/access-check-solver-beacon.log)件 / $(awk '{print $1}' /tmp/access-check-solver-beacon.log | sort -u | wc -l) IP(/human-checkの送り元 $(wc -l < /tmp/access-check-solver-ips.txt) IP)"
: > /tmp/access-check-beacon-senders.txt
if [ -s /tmp/access-check-beacon.log ]; then
    echo "件数: $(wc -l < /tmp/access-check-beacon.log)"
    echo "ユニークIP数: $(wc -l < /tmp/access-check-beacon-ips.txt)"
    # JSを実行するヘッドレスブラウザ型のボットもビーコンを送るため(Hetznerで実例あり)、
    # 送信元ごとに逆引きを出し、データセンター・スキャナー由来には * を付ける。
    # 人かどうかを手で判断する材料を、送信元ごとに{ }で並べる(2026-11-25)。端末の種類(ビーコンのUA)、その日の入口
    # (検索・X・AIのRefererか?utm_source=chatgpt)、ボタンの段を押したか。11/18〜11/25に人と判断した決め手はこの3つと事業者だった。
    awk -F'"' "$KIND_FN"'
    FILENAME == ARGV[1] { b[$1]; next }
    {
        split($1, a, " "); ip = a[1]; if (!(ip in b)) next;
        split($2, r, " "); split($3, s, " ");
        if (r[1] == "POST" && r[2] ~ /^\/beacon/ && !(ip in k)) k[ip] = ($6 ~ /Twitter for iPhone|TwitterAndroid/) ? "Xアプリ" : kind($6);
        if (r[1] == "GET" && r[2] !~ /^\/(beacon|css\/|js\/|images\/|favicon)/) {
            if ($4 ~ /^https?:\/\/([a-z0-9-]+\.)*(google|bing|duckduckgo|yahoo|yandex|ecosia|naver|baidu|qwant|seznam|startpage)\.[a-z.]+(\/|$)|search\.brave\.com|googlequicksearchbox/) e[ip, "検索から"];
            if ($4 ~ /^https?:\/\/(t\.co|x\.com|twitter\.com)\//) e[ip, "Xから"];
            if ($4 ~ /chatgpt\.com|perplexity\.ai|gemini\.google|copilot\.microsoft|claude\.ai/ || r[2] ~ /utm_source=chatgpt/) e[ip, "AIから"];
        }
        if (r[2] ~ /^\/human-check\/press/ && s[1] == 204) e[ip, "ボタンを押した"];
    }
    END { for (ip in k) { h = k[ip]; for (x in e) { split(x, xx, SUBSEP); if (xx[1] == ip) h = h "・" xx[2] } print ip "\t" h } }' \
        /tmp/access-check-beacon-ips.txt /tmp/access-check-window.log > /tmp/access-check-sender-hints.txt
    echo "-- 送信元IP別(* はデータセンター・スキャナー由来。逆引きのホスト名か、[ ]の中の回線の事業者で判定。{ }は端末・入口・ボタン) --"
    awk '{print $1}' /tmp/access-check-beacon.log | sort | uniq -c | while read -r cnt ip; do
        name="$(rdns_of "$ip") [$(asn_of "$ip")]"
        if printf '%s' "$name" | grep -qP "$DC_REGEX|$SCANNER_REGEX" || grep -qxF "$ip" /tmp/access-check-hosting-ips.txt; then mark='*'; else mark=' '; fi
        printf '%s %3d %-16s %s {%s}\n' "$mark" "$cnt" "$ip" "$name" "$(grep -m1 -P "^$ip\t" /tmp/access-check-sender-hints.txt | cut -f2)"
        printf '%s\t%s\t%s\t%s\n' "$mark" "$cnt" "$ip" "$name" >> /tmp/access-check-beacon-senders.txt
    done
    # ビーコンにはページのURLのfrom=がそのまま載る(/beacon?p=...&from=xreply)。入ってきたページの
    # 表示ごとに1件なので、ページのURLで数えるのと違ってクローラーが混ざらない。
    echo "-- 流入元タグ(?from=)別(* の送信元を除く。件数=入ってきたページの表示、人=ユニークIP) --"
    awk -F'\t' '$1 != "*" {print $3}' /tmp/access-check-beacon-senders.txt > /tmp/access-check-beacon-real-ips.txt
    awk 'NR == FNR {real[$1]; next}
         ($1 in real) && match($7, /[?&]from=[A-Za-z0-9_-]+/) {print substr($7, RSTART + 6, RLENGTH - 6), $1}' \
        /tmp/access-check-beacon-real-ips.txt /tmp/access-check-beacon.log \
      | awk '{total++; n[$1]++; if (!(($1, $2) in seen)) {seen[$1, $2]; u[$1]++}}
             END {if (total == 0) print "  (なし)"; for (t in n) printf "  from=%s: %d件 / %d人\n", t, n[t], u[t]}'
    # 投稿経由とリプライ経由は日別の記録(daily.csvのx_post・x_reply)に分けて残す(2026-11-10から)。両方から来た人は両方に数えるので、内訳の和が人数より多い日がある。
    x_people() { awk -v re="$1" 'NR == FNR {real[$1]; next} ($1 in real) && $7 ~ re {print $1}' /tmp/access-check-beacon-real-ips.txt /tmp/access-check-beacon.log | sort -u | wc -l; }
    echo "  X(from=x・xreply)の人数: $(x_people '[?&]from=(x|xreply)(&| |$)')(投稿 from=x: $(x_people '[?&]from=x(&| |$)')人・リプライ from=xreply: $(x_people '[?&]from=xreply(&| |$)')人)"
    # 投稿ごとの「来た人」(X運用記録.mdの提案と結果)は、着地URLの&v=で分けて数える。Refererが着地URL。
    echo "-- X経由のビーコンの明細(* の送信元を除く。時刻はUTC。着地URLの&v=で投稿に割り当てる) --"
    awk 'NR == FNR {real[$1]; next}
         ($1 in real) && $7 ~ /[?&]from=(x|xreply)(&| |$)/ {
             ref = $0; sub(/^[^"]*"[^"]*" [0-9]+ [^ ]+ "/, "", ref); sub(/".*/, "", ref); sub(/^https:\/\/[^\/]+/, "", ref);
             ua = $0; sub(/.*" "/, "", ua); sub(/"$/, "", ua);
             if (ua ~ /Twitter for iPhone/) k = "iPhoneのXアプリ"; else if (ua ~ /TwitterAndroid/) k = "AndroidのXアプリ";
             else if (ua ~ /iPhone|iPad/) k = "iPhone/iPad"; else if (ua ~ /Android/) k = "Android"; else k = "PC・その他";
             r = ($7 ~ /[?&]r=1/) ? " r=1" : "";
             printf "  %s %-16s %-14s%s %s\n", substr($4, 2), $1, k, r, ref}' \
        /tmp/access-check-beacon-real-ips.txt /tmp/access-check-beacon.log | sort -t' ' -k1,1 || true
else
    if [ -s /tmp/access-check-beacon-all.log ]; then
        echo "除外後: 0件(本人・既知ノイズのIPからのビーコンしか無かった)"
    else
        echo "除外後: 0件(除外前も0件。ビーコンの仕組み自体が動いているか確認すること)"
    fi
fi
touch /tmp/access-check-beacon-real-ips.txt

# 画面(機能)ごとの表示回数(2026-11-10追加)。名前は画面一覧.mdに揃える。実訪問・PVと同じく * の送信元を除くので、件数の合計はPVと同じになる。
# ビーコンの p= はパスだけなので、使用率順・国別・カードでの絞り込みはRefererに載るページのURLで見分ける
# (Referrer-Policyがstrict-origin-when-cross-originで、同じサイトへのsendBeaconには表示中のURLが全部載る)。
# 行頭が @screen の行は、ローカル側でhistory/screens.csvに書き、画面には出さない。
echo
echo "== 画面別(ビーコン。* の送信元を除く。件数=表示回数、人=ユニークIP。件数の合計はPVと同じ) =="
awk -F'"' '
function screen(p) {
    if (p == "/") return "トップページ";
    if (p == "/ranking/players") return "個人ランキング画面";
    if (p == "/ranking/players/movements") return "ランキングの動き画面";
    if (p ~ /^\/ranking\/players\/movements\/[^\/]+$/) return "日ごとのランキングの動き画面";
    if (p == "/ranking/players/seasons") return "シーズンの一覧画面";
    if (p ~ /^\/ranking\/players\/seasons\/[^\/]+$/) return "シーズンの最終順位画面";
    if (p == "/ranking") return "クランランキング画面";
    if (p == "/player/search") return "プレイヤー名検索結果画面";
    if (p ~ /^\/player\/[^\/]+\/battles\/[^\/]+$/) return "対戦詳細画面";
    if (p ~ /^\/player\/[^\/]+$/) return "プレイヤー情報画面";
    if (p == "/clan/search") return "クラン名検索結果画面";
    if (p ~ /^\/clan\/[^\/]+$/) return "クラン情報画面";
    if (p == "/cards") return "カード一覧画面";
    if (p ~ /^\/card\/[^\/]+$/) return "カード詳細画面";
    if (p == "/decks") return "トッププレイヤーのデッキ画面";
    if (p == "/decks/popular") return "人気のデッキ画面";
    if (p == "/decks/win-rates") return "デッキの勝率画面";
    if (p == "/pros") return "プロ・クリエイターのデッキ画面";
    if (p == "/balance") return "バランス調整画面(一覧)";
    if (p ~ /^\/balance\/[^\/]+$/) return "バランス調整画面(調整ごと)";
    if (p == "/favorites") return "お気に入り画面";
    if (p == "/about") return "このサイトについて・お問い合わせ";
    if (p == "/privacy") return "プライバシーポリシー";
    if (p == "/quiz") return "クイズ画面";
    return "";
}
function feature(name, s, cond, code) {
    if (!(s in fparent)) {fparent[s]; forder[++nf] = s};
    fname[s] = name;
    if (cond) {fc[s]++; if (!((s, ip) in fseen)) {fseen[s, ip]; fu[s]++}; if (code != "") fcode[s, code]++};
}
BEGIN {
    nscreens = split("トップページ|個人ランキング画面|ランキングの動き画面|日ごとのランキングの動き画面|シーズンの一覧画面|シーズンの最終順位画面|クランランキング画面|プレイヤー情報画面|プレイヤー名検索結果画面|対戦詳細画面|クラン情報画面|クラン名検索結果画面|カード一覧画面|カード詳細画面|トッププレイヤーのデッキ画面|人気のデッキ画面|デッキの勝率画面|プロ・クリエイターのデッキ画面|バランス調整画面(一覧)|バランス調整画面(調整ごと)|お気に入り画面|このサイトについて・お問い合わせ|プライバシーポリシー|クイズ画面", all, "|");
}
FILENAME == ARGV[1] {real[$1]; next}
{
    split($1, a, " "); ip = a[1];
    if (!(ip in real)) next;
    split($2, r, " "); url = r[2];
    p = ""; if (match(url, /[?&]p=[^&]*/)) p = substr(url, RSTART + 3, RLENGTH - 3);
    gsub(/%2[Ff]/, "/", p);
    s = screen(p);
    if (s == "") {other[p]++; s = "その他"};
    total++; c[s]++;
    if (!((s, ip) in seen)) {seen[s, ip]; u[s]++};
    if (!(ip in people)) {people[ip]; npeople++};

    if (s != "トップページ" && s != "個人ランキング画面" && s != "クランランキング画面" && s != "カード一覧画面" \
        && s != "トッププレイヤーのデッキ画面" && s != "人気のデッキ画面") next;
    ref = $4; refpath = ref; refquery = "";
    sub(/^https?:\/\/[^\/]+/, "", refpath);
    if (index(refpath, "?") > 0) {refquery = substr(refpath, index(refpath, "?")); refpath = substr(refpath, 1, index(refpath, "?") - 1)};
    if (refpath == "") refpath = "/";
    if (ref !~ /^https?:\/\// || refpath != p) {noref++; next};
    code = ""; if (match(refquery, /[?&]country=[A-Za-z]+/)) code = toupper(substr(refquery, RSTART + 9, RLENGTH - 9));
    if (s == "カード一覧画面") feature("使用率順(?sort=usage)", s, refquery ~ /[?&]sort=usage(&|$)/, "");
    else if (s == "トッププレイヤーのデッキ画面" || s == "人気のデッキ画面") feature("カードで絞り込み(?card=)", s, refquery ~ /[?&]card=/, "");
    else feature("国別(?country=)", s, code != "", code);
}
END {
    if (total == 0) {print "  (なし)"; exit};
    for (s in c) printf "%d\t%d\t%s\n", c[s], u[s], s | "sort -t\"\t\" -k1,1nr -k2,2nr";
    close("sort -t\"\t\" -k1,1nr -k2,2nr");
    printf "  合計: %d件 / %d人\n", total, npeople;
    for (p in other) printf "  その他の内訳: %s %d件\n", (p == "" ? "(p=なし)" : p), other[p];
    zero = "";
    for (i = 1; i <= nscreens; i++) if (!(all[i] in c)) zero = zero (zero == "" ? "" : "、") all[i];
    if (zero != "") print "  表示0の画面: " zero;
    if (nf > 0) {
        print "  -- 機能の内訳(ビーコンのRefererに載った画面のURLから) --";
        for (i = 1; i <= nf; i++) {
            s = forder[i]; codes = "";
            for (k in fcode) {split(k, kk, SUBSEP); if (kk[1] == s) codes = codes (codes == "" ? "" : ", ") kk[2] " " fcode[k]};
            printf "  %sのうち%s: %d件 / %d人%s\n", s, fname[s], fc[s], fu[s], (codes == "" ? "" : "(" codes ")");
        }
    }
    if (noref > 0) printf "  (Refererが無く、内訳を判定できなかった表示: %d件)\n", noref;
    for (s in c) printf "@screen\t%s\t%d\t%d\n", s, c[s], u[s];
}' /tmp/access-check-beacon-real-ips.txt /tmp/access-check-beacon.log \
  | awk -F'\t' '/^@screen\t/ {print; next} NF == 3 {printf "  %4d件 / %3d人  %s\n", $1, $2, $3; next} {print}'

echo
echo "== クイズの操作(ビーコンの e=。* の送信元を除く。件数 / 人=ユニークIP) =="
if [ -s /tmp/access-check-quiz-events.log ]; then
    awk 'NR == FNR {real[$1]; next} ($1 in real) {
            e = $7; sub(/.*[?&]e=/, "", e); sub(/&.*/, "", e);
            n = "?"; if ($7 ~ /[?&]n=/) {n = $7; sub(/.*[?&]n=/, "", n); sub(/&.*/, "", n)}
            k = e " (" n "問)"; total++; c[k]++; if (!((k, $1) in seen)) {seen[k, $1]; u[k]++}
        }
        END {if (total == 0) print "  (なし)"; for (k in c) printf "  %s: %d件 / %d人\n", k, c[k], u[k]}' \
        /tmp/access-check-beacon-real-ips.txt /tmp/access-check-quiz-events.log | sort
else
    echo "  (なし)"
fi
echo

# 再訪(前日以前にも来た人。2026-10-15追加)。目印は2つあり、どちらか一方でも当たれば再訪の候補とする。
# - 目印: ビーコンの r=1。ブラウザが初めて来た日時を覚えていて、別の日の訪問なら付ける。IPが変わっても分かるが、
#   Xアプリ内のブラウザとSafariのように保存領域が別になると分からない(iPhoneのSafariは7日使わないと消す)。
# - 前の来訪: 同じIPから前日以前にビーコンが来ていた日。記録が残る約5週間分だけ見る。入口が変わっても
#   同じ回線なら分かるが、携帯回線やVPNはIPが変わるので取りこぼす(9/29→9/30のドコモの人で実例あり)。
# 前日以前のビーコンは、日次集計(clash-royale-access-stats.sh)がUTCの日ごとに残したファイルから読む。以前は
# 約5週間分の全ログ(11月で約5GB)を毎回読み直していた(2026-11-16に変更)。範囲の開始を含むUTCの日は、
# 日次集計(日本時間10:00)の前だとまだファイルが無いので、そのときだけその日の分をログから取る。
echo
echo "== 再訪(前日以前にも来た人) =="
if [ -s /tmp/access-check-beacon.log ]; then
    BEACON_DIR=/var/log/clash-royale-api/beacons
    FIRST_DAY=$(date -u -d "@$START_EPOCH" +%Y-%m-%d)
    declare -A HAVE_DAY=()
    PAST_FILES=()
    while read -r f; do
        d=${f##*/beacon-}; d=${d%.log}
        if [[ ! "$d" > "$FIRST_DAY" ]]; then HAVE_DAY[$d]=1; PAST_FILES+=("$f"); fi
    done < <(sudo find "$BEACON_DIR" -maxdepth 1 -name 'beacon-*.log' 2> /dev/null | sort)
    FIRST_DAY_LOGS=()
    if [ -z "${HAVE_DAY[$FIRST_DAY]:-}" ]; then
        mapfile -t FIRST_DAY_LOGS < <(sudo find /var/log/httpd -maxdepth 1 -name 'access_log*' -newermt "@$(date -u -d "$FIRST_DAY" +%s)" | sort)
    fi
    if [ "${#PAST_FILES[@]}" -eq 0 ]; then
        echo "(前日以前のビーコンの記録がまだ無い。日次集計がbeacons/に残していく)"
    else
        OLDEST_DAY=${PAST_FILES[0]##*/beacon-}; OLDEST_DAY=${OLDEST_DAY%.log}
        MISSING=""
        d_epoch=$(date -u -d "$OLDEST_DAY" +%s)
        while d=$(date -u -d "@$d_epoch" +%Y-%m-%d); [[ "$d" < "$FIRST_DAY" ]]; do
            [ -n "${HAVE_DAY[$d]:-}" ] || MISSING="$MISSING $d"
            d_epoch=$((d_epoch + 86400))
        done
        echo "(前日以前のビーコンは、日次集計の記録 $OLDEST_DAY〜 から読んだ。日付はUTC$([ "${#FIRST_DAY_LOGS[@]}" -eq 0 ] || echo "。$FIRST_DAY はまだ記録が無いのでログから読んだ")$([ -z "$MISSING" ] || echo "。記録が無い日:$MISSING"))"
    fi
    {
        [ "${#PAST_FILES[@]}" -eq 0 ] || sudo cat "${PAST_FILES[@]}"
        if [ "${#FIRST_DAY_LOGS[@]}" -gt 0 ]; then
            sudo zcat -f "${FIRST_DAY_LOGS[@]}" | { LC_ALL=C grep -F "[$DAY_FROM:" || true; } | { LC_ALL=C grep -F '"POST /beacon' || true; }
        fi
    } | { grep -viE "$BOT_REGEX" || true; } | { grep -vE "^($NOISE_REGEX) " || true; } \
      | awk -v start="$START_KEY" '
        BEGIN {
            split("Jan Feb Mar Apr May Jun Jul Aug Sep Oct Nov Dec", months, " ");
            for (i = 1; i <= 12; i++) monnum[months[i]] = i;
        }
        {
            if (!match($0, /\[([0-9]{2})\/([A-Za-z]{3})\/([0-9]{4}):([0-9]{2}):([0-9]{2}):([0-9]{2})/, m)) next;
            key = sprintf("%04d%02d%02d%02d%02d%02d", m[3], monnum[m[2]], m[1], m[4], m[5], m[6]);
            if (key >= start) next;
            utc = mktime(sprintf("%d %d %d %d %d %d", m[3], monnum[m[2]], m[1], m[4], m[5], m[6]), 1);
            print $1, strftime("%m/%d", utc + 9 * 3600, 1);
        }' | sort -u > /tmp/access-check-beacon-past.txt
    echo "(目印=その日のビーコンのうち r=1 が付いた件数。前の来訪=同じIPから前日以前にビーコンがあった日(日本時間)。* はデータセンター・スキャナー由来)"
    : > /tmp/access-check-revisit-ips.txt
    while IFS=$'\t' read -r mark cnt ip name; do
        flagged=$(awk -v ip="$ip" '$1 == ip && $7 ~ /[?&]r=1(&|$)/' /tmp/access-check-beacon.log | wc -l)
        past=$(awk -v ip="$ip" '$1 == ip {print $2}' /tmp/access-check-beacon-past.txt | sort | tail -5 | paste -sd ',' -)
        printf '%s %-16s 目印 %s/%s件  前の来訪: %s  (%s)\n' "$mark" "$ip" "$flagged" "$cnt" "${past:-なし}" "$name"
        if [ "$mark" != '*' ] && { [ "$flagged" -gt 0 ] || [ -n "$past" ]; }; then echo "$ip" >> /tmp/access-check-revisit-ips.txt; fi
    done < /tmp/access-check-beacon-senders.txt
    echo "-- 再訪の候補(データセンター以外で、目印か前の来訪のどちらかあり) --"
    echo "人数(ユニークIP): $(wc -l < /tmp/access-check-revisit-ips.txt)"
else
    echo "(ビーコンなし)"
fi

echo
echo "== 検索エンジン経由の訪問 =="
if [ -s /tmp/access-check-search.log ]; then
    echo "(時刻はUTC。先頭の * はデータセンター由来。「ビーコンあり」は同じIPからブラウザでの表示が記録されたもの)"
    while read -r ip time path host; do
        if grep -qxF "$ip" /tmp/access-check-machine-ips.txt; then mark='*'; else mark=' '; fi
        if grep -qxF "$ip" /tmp/access-check-beacon-ips.txt; then shown='ビーコンあり'; else shown='ビーコンなし'; fi
        printf '%s %-16s %s %s %s %s (%s [%s])\n' "$mark" "$ip" "$time" "$shown" "$host" "$path" "$(rdns_of "$ip")" "$(asn_of "$ip")"
    done < /tmp/access-check-search.log
    # Googleの先読み(Chromeが検索結果のページを、Googleの中継サーバー(逆引きがgooglezip・google-proxy)を通して先に取っておく)
    # から開いた人は、HTMLは中継サーバーのIPに、ビーコンだけ本人の回線のIPに残り、同じIPでは結び付かない。
    # 先読みから10分以内に同じページのビーコンが別のIPから届いたら、その人も検索から来た人に数える(2026-11-25追加。
    # 11/18〜11/24で5人。ページで結び付けるので、プレイヤー・クラン・カード詳細のように人ごとに違うページだけを見る)。
    awk '{print $1}' /tmp/access-check-search.log | sort -u | while read -r ip; do
        case "$(rdns_of "$ip")" in *googlezip*|*google-proxy*) echo "$ip" ;; esac
    done > /tmp/access-check-prefetch-ips.txt
    awk '
    function ts(s,   m, mon) {
        if (!match(s, /([0-9]{2})\/([A-Za-z]{3})\/([0-9]{4}):([0-9]{2}):([0-9]{2}):([0-9]{2})/, m)) return 0;
        mon = (index("JanFebMarAprMayJunJulAugSepOctNovDec", m[2]) + 2) / 3;
        return mktime(sprintf("%d %d %d %d %d %d", m[3], mon, m[1], m[4], m[5], m[6]), 1);
    }
    FILENAME == ARGV[1] { pf[$1]; next }
    FILENAME == ARGV[2] { machine[$1]; next }
    FILENAME == ARGV[3] {
        if (!($1 in pf)) next;
        p = $3; sub(/\?.*/, "", p); if (p !~ /^\/(player|clan|card)\//) next;
        n++; pt[n] = ts($2); pp[n] = p; next;
    }
    {
        if (($1 in pf) || ($1 in machine) || !match($7, /[?&]p=[^&]*/)) next;
        p = substr($7, RSTART + 3, RLENGTH - 3); gsub(/%2[Ff]/, "/", p); t = ts($4);
        if (($1, p) in seen) next;
        best = -1;
        for (i = 1; i <= n; i++) if (pp[i] == p && t >= pt[i] && t - pt[i] <= 600 && (best < 0 || t - pt[i] < best)) best = t - pt[i];
        if (best >= 0) { seen[$1, p]; print $1, substr($4, 2), p, best }
    }' /tmp/access-check-prefetch-ips.txt /tmp/access-check-machine-ips.txt /tmp/access-check-search.log /tmp/access-check-beacon.log \
        > /tmp/access-check-search-prefetch.log
    if [ -s /tmp/access-check-search-prefetch.log ]; then
        echo "-- Googleの先読みから開いた人(先読みの後、同じページのビーコンが本人の回線から届いた。時刻はUTC) --"
        while read -r ip time path sec; do
            printf '  %-16s %s %s(先読みの%s秒後) (%s [%s])\n' "$ip" "$time" "$path" "$sec" "$(rdns_of "$ip")" "$(asn_of "$ip")"
        done < /tmp/access-check-search-prefetch.log
    fi
    echo "-- 実訪問(データセンター以外で、ビーコンあり。先読みから開いた人を含む) --"
    awk '{print $1}' /tmp/access-check-search.log | sort -u \
      | grep -vxFf /tmp/access-check-machine-ips.txt \
      | grep -xFf /tmp/access-check-beacon-ips.txt > /tmp/access-check-search-real-ips.txt || true
    awk '{print $1}' /tmp/access-check-search-prefetch.log | sort -u > /tmp/access-check-search-prefetch-ips.txt
    echo "人数(ユニークIP): $(sort -u /tmp/access-check-search-real-ips.txt /tmp/access-check-search-prefetch-ips.txt | wc -l)(うち先読みから開いた人 $(grep -vxFf /tmp/access-check-search-real-ips.txt /tmp/access-check-search-prefetch-ips.txt | wc -l))"
    # 先読みのページは、本人の回線からも検索から開いて数えた人の分を足さない(同じページを二重に数えるため)。
    echo "検索から開いたページ数: $(( $(awk 'NR == FNR {real[$1]; next} ($1 in real)' /tmp/access-check-search-real-ips.txt /tmp/access-check-search.log | wc -l) + $(awk 'FILENAME == ARGV[1] {real[$1]; next} !($1 in real)' /tmp/access-check-search-real-ips.txt /tmp/access-check-search-prefetch.log | wc -l) ))"
    echo "-- 検索エンジン別(全件) --"
    awk '{print $4}' /tmp/access-check-search.log | sort | uniq -c | sort -rn
else
    echo "(なし)"
fi
REMOTE

grep -v $'^@screen\t' "$REMOTE_OUT" || true
if [ "$SSH_STATUS" -ne 0 ]; then
    echo "(VM側の集計が途中で失敗した: 終了コード $SSH_STATUS。screens.csvは書き換えていない)" >&2
    exit "$SSH_STATUS"
fi

# 画面別の件数を1日ずつ残す(グラフの元データのdaily.csvと同じ扱いで、masterに直接コミットしてよい)。
# 同じ日を集計し直したら、その日の行を置き換える。日の途中の集計はpartial_untilに日本時間の時刻を入れる。
SCREENS_CSV="$SCRIPT_DIR/../history/screens.csv"
[ -f "$SCREENS_CSV" ] || echo "date,screen,pv,visitors,partial_until" > "$SCREENS_CSV"
PARTIAL_UNTIL=""
if [ "$END_EPOCH" -lt "$END_EPOCH_FULLDAY" ]; then
    PARTIAL_UNTIL=$(TZ=$JST_TZ date -d "@$END_EPOCH" +%H:%M)
fi
{
    head -1 "$SCREENS_CSV"
    { tail -n +2 "$SCREENS_CSV" | awk -F, -v d="$JST_DATE" '$1 != d'
      { grep $'^@screen\t' "$REMOTE_OUT" || true; } | sort -t$'\t' -k3,3nr \
        | awk -F'\t' -v d="$JST_DATE" -v until="$PARTIAL_UNTIL" '{printf "%s,%s,%d,%d,%s\n", d, $2, $3, $4, until}'
    } | sort -t, -k1,1 -s
} > "$SCREENS_CSV.tmp"
mv "$SCREENS_CSV.tmp" "$SCREENS_CSV"
