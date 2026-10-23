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

ssh -i "$SSH_KEY" -o ConnectTimeout=10 "$HOST" bash -s -- "$JST_DATE" "$START_EPOCH" "$END_EPOCH" "$NOISE_IPS_B64" <<'REMOTE'
set -euo pipefail
JST_DATE="$1"; START_EPOCH="$2"; END_EPOCH="$3"; NOISE_REGEX=$(echo "$4" | base64 -d)

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

# ホスティング事業者・クラウドを示すホスト名の断片。当たった=人間の家庭回線ではない。
# `v22025112337...` のような数字だけの長いホスト名はVPSの自動採番(Contabo系)の典型。
DC_REGEX='ovh\.|ovh\.net|your-server\.de|hetzner|amazonaws|digitalocean|linode|vultr|choopa|contabo|scaleway|leaseweb|m247|oraclecloud|azure|googleusercontent|hostwinds|colocrossing|datacenter|\.cloud\.|vps|dedicated|servers?\.|srv[0-9]*\.[a-z]+$|srv\.(de|net|com)|\bv[0-9]{8,}\.|p14\.io|hwclouds|pfcloud'
# 素性を名乗っている大手スキャナー・インターネット調査プロジェクト。逆引き名にそのまま出る。
# UAはブラウザを偽装していても逆引きは自分のドメインのままなので、ここで捕まえられる。
SCANNER_REGEX='googlebot|crawl-|censys|shodan|internet-measurement|internet-census|bufferover\.run|criminalip|deepfield|infrawat|no-reverse-dns-configured|rwth-aachen|uni-[a-z]+\.de|scan\.|\bscanner|netsystemsresearch|binaryedge|onyphe|driftnet|stretchoid|alphastrike|securitytrails|leakix|palo ?alto|expanse|group-ib'
grep -P "\t.*($DC_REGEX|$SCANNER_REGEX)" /tmp/access-check-rdns.txt | cut -f1 | sort -u > /tmp/access-check-dc-ips.txt || true

# 逆引きが引けないIP。一般のISP(固定回線・モバイルとも)はほぼ必ずPTRを設定しているため、
# 「逆引きなし」はデータセンター・スキャナーの強い目印になる(検索経由の判定にだけ使う)。
grep -P '\t-$' /tmp/access-check-rdns.txt | cut -f1 | sort -u > /tmp/access-check-nordns-ips.txt || true

# t.co経由を装う分散スキャナー群の目印(2026-09-22発見)。多数の別IPが揃って
# 同じ古いChromeのUAを名乗る。実在ユーザーの分布としては起こり得ない。
STALE_UA='Chrome/103\.0\.0\.0 Safari'
grep -E "$STALE_UA" /tmp/access-check-clean.log | awk '{print $1}' | sort -u > /tmp/access-check-staleua-ips.txt || true

cat /tmp/access-check-dc-ips.txt /tmp/access-check-staleua-ips.txt /tmp/access-check-nordns-ips.txt \
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
: > /tmp/access-check-beacon-senders.txt
if [ -s /tmp/access-check-beacon.log ]; then
    echo "件数: $(wc -l < /tmp/access-check-beacon.log)"
    echo "ユニークIP数: $(wc -l < /tmp/access-check-beacon-ips.txt)"
    # JSを実行するヘッドレスブラウザ型のボットもビーコンを送るため(Hetznerで実例あり)、
    # 送信元ごとに逆引きを出し、データセンター・スキャナー由来には * を付ける。
    echo "-- 送信元IP別(* はデータセンター・スキャナー由来) --"
    awk '{print $1}' /tmp/access-check-beacon.log | sort | uniq -c | while read -r cnt ip; do
        name=$(rdns_of "$ip")
        if printf '%s' "$name" | grep -qP "$DC_REGEX|$SCANNER_REGEX"; then mark='*'; else mark=' '; fi
        printf '%s %3d %-16s %s\n' "$mark" "$cnt" "$ip" "$name"
        printf '%s\t%s\t%s\t%s\n' "$mark" "$cnt" "$ip" "$name" >> /tmp/access-check-beacon-senders.txt
    done
    echo "-- 表示されたページ別 --"
    cat /tmp/access-check-beacon.log \
      | grep -ohE 'p=[^& ]+' | sed 's/^p=//' \
      | sed -E 's#%2F#/#g; s#/player/[^/?]+#/player/{tag}#; s#/clan/[^/?]+#/clan/{tag}#; s#/card/[^/?]+#/card/{id}#' \
      | sort | uniq -c | sort -rn | head -20 || true
    # ビーコンにはページのURLのfrom=がそのまま載る(/beacon?p=...&from=xreply)。入ってきたページの
    # 表示ごとに1件なので、ページのURLで数えるのと違ってクローラーが混ざらない。
    echo "-- 流入元タグ(?from=)別(* の送信元を除く。件数=入ってきたページの表示、人=ユニークIP) --"
    awk -F'\t' '$1 != "*" {print $3}' /tmp/access-check-beacon-senders.txt > /tmp/access-check-beacon-real-ips.txt
    awk 'NR == FNR {real[$1]; next}
         ($1 in real) && match($7, /[?&]from=[A-Za-z0-9_-]+/) {print substr($7, RSTART + 6, RLENGTH - 6), $1}' \
        /tmp/access-check-beacon-real-ips.txt /tmp/access-check-beacon.log \
      | awk '{n[$1]++; if (!(($1, $2) in seen)) {seen[$1, $2]; u[$1]++}}
             END {if (length(n) == 0) print "  (なし)"; for (t in n) printf "  from=%s: %d件 / %d人\n", t, n[t], u[t]}'
    echo "  X(from=x・xreply)の人数: $(awk 'NR == FNR {real[$1]; next} ($1 in real) && $7 ~ /[?&]from=(x|xreply)(&| |$)/ {print $1}' /tmp/access-check-beacon-real-ips.txt /tmp/access-check-beacon.log | sort -u | wc -l)"
else
    if [ -s /tmp/access-check-beacon-all.log ]; then
        echo "除外後: 0件(本人・既知ノイズのIPからのビーコンしか無かった)"
    else
        echo "除外後: 0件(除外前も0件。ビーコンの仕組み自体が動いているか確認すること)"
    fi
fi

# 再訪(前日以前にも来た人。2026-10-15追加)。目印は2つあり、どちらか一方でも当たれば再訪の候補とする。
# - 目印: ビーコンの r=1。ブラウザが初めて来た日時を覚えていて、別の日の訪問なら付ける。IPが変わっても分かるが、
#   Xアプリ内のブラウザとSafariのように保存領域が別になると分からない(iPhoneのSafariは7日使わないと消す)。
# - 前の来訪: 同じIPから前日以前にビーコンが来ていた日。ログが残る約5週間分だけ見る。入口が変わっても
#   同じ回線なら分かるが、携帯回線やVPNはIPが変わるので取りこぼす(9/29→9/30のドコモの人で実例あり)。
echo
echo "== 再訪(前日以前にも来た人) =="
if [ -s /tmp/access-check-beacon.log ]; then
    mapfile -t ALL_LOG_FILES < <(sudo find /var/log/httpd -maxdepth 1 -name 'access_log*' | sort)
    sudo zcat -f "${ALL_LOG_FILES[@]}" | { LC_ALL=C grep -F '"POST /beacon' || true; } \
      | { grep -viE "$BOT_REGEX" || true; } | { grep -vE "^($NOISE_REGEX) " || true; } \
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
        printf '%s %-16s %s %s %s %s (%s)\n' "$mark" "$ip" "$time" "$shown" "$host" "$path" "$(rdns_of "$ip")"
    done < /tmp/access-check-search.log
    echo "-- 実訪問(データセンター以外で、ビーコンあり) --"
    awk '{print $1}' /tmp/access-check-search.log | sort -u \
      | grep -vxFf /tmp/access-check-machine-ips.txt \
      | grep -xFf /tmp/access-check-beacon-ips.txt > /tmp/access-check-search-real-ips.txt || true
    echo "人数(ユニークIP): $(wc -l < /tmp/access-check-search-real-ips.txt)"
    echo "検索から開いたページ数: $(awk 'NR == FNR {real[$1]; next} ($1 in real)' /tmp/access-check-search-real-ips.txt /tmp/access-check-search.log | wc -l)"
    echo "-- 検索エンジン別(全件) --"
    awk '{print $4}' /tmp/access-check-search.log | sort | uniq -c | sort -rn
else
    echo "(なし)"
fi
REMOTE
