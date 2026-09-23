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

# JST日付の00:00:00〜翌日00:00:00(現在時刻がその前ならそこまで)をUTC epoch秒に変換する。
START_EPOCH=$(TZ=$JST_TZ date -d "$JST_DATE 00:00:00" +%s)
END_EPOCH_FULLDAY=$(TZ=$JST_TZ date -d "$JST_DATE 00:00:00 +1 day" +%s)
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

# ここから先はデータセンター由来の切り分け。IPのCIDR一覧を手で持つと保守しきれないため、
# 逆引き(rDNS)のホスト名でホスティング事業者を判定する。事業者は自分のIPにその事業者と
# 分かるホスト名を付けているので、CIDRを追いかけるより変化に強い。
# 逆引きは1IPにつき1回だけ引き、結果は /tmp/access-check-rdns.txt に持つ。
awk '{print $1}' /tmp/access-check-clean.log | sort -u > /tmp/access-check-ips.txt
: > /tmp/access-check-rdns.txt
if command -v dig >/dev/null 2>&1; then
    lookup() { dig +short +time=2 +tries=1 -x "$1" 2>/dev/null | head -1; }
else
    # digが無い環境向け。getentはrDNSも引くが、引けないとき非0で終わるので握りつぶす。
    lookup() { getent hosts "$1" 2>/dev/null | awk '{print $2}' | head -1; }
fi
while read -r ip; do
    name=$(lookup "$ip") || true
    # 引けなかったときdigは ";; connection timed out..." のような文を返す。ホスト名の形を
    # していないものは「逆引きなし」として扱う。x.y.z.in-addr.arpa. は事業者の手がかりが
    # 無い(PTRを形式的に置いているだけ)ので、同じく逆引きなし扱いにする。
    case "$name" in
        *' '*|'') name='-' ;;
        *in-addr.arpa.) name='-' ;;
    esac
    printf '%s\t%s\n' "$ip" "$name" >> /tmp/access-check-rdns.txt
done < /tmp/access-check-ips.txt

# ホスティング事業者・クラウドを示すホスト名の断片。当たった=人間の家庭回線ではない。
# `v22025112337...` のような数字だけの長いホスト名はVPSの自動採番(Contabo系)の典型。
DC_REGEX='ovh\.|ovh\.net|your-server\.de|hetzner|amazonaws|digitalocean|linode|vultr|choopa|contabo|scaleway|leaseweb|m247|oraclecloud|azure|googleusercontent|hostwinds|colocrossing|datacenter|\.cloud\.|vps|dedicated|servers?\.|srv[0-9]*\.[a-z]+$|srv\.(de|net|com)|\bv[0-9]{8,}\.|p14\.io|hwclouds|pfcloud'
# 素性を名乗っている大手スキャナー・インターネット調査プロジェクト。逆引き名にそのまま出る。
# UAはブラウザを偽装していても逆引きは自分のドメインのままなので、ここで捕まえられる。
SCANNER_REGEX='googlebot|crawl-|censys|shodan|internet-measurement|internet-census|bufferover\.run|criminalip|deepfield|infrawat|no-reverse-dns-configured|rwth-aachen|uni-[a-z]+\.de|scan\.|\bscanner|netsystemsresearch|binaryedge|onyphe|driftnet|stretchoid|alphastrike|securitytrails|leakix|palo ?alto|expanse'
grep -P "\t.*($DC_REGEX|$SCANNER_REGEX)" /tmp/access-check-rdns.txt | cut -f1 | sort -u > /tmp/access-check-dc-ips.txt || true

# 逆引きが引けないIP。一般のISP(固定回線・モバイルとも)はほぼ必ずPTRを設定しているため、
# 「逆引きなし」はデータセンター・スキャナーの強い目印になる。ただし断定はできないので
# 上のDC判定とは別に数え、内訳に出す。
grep -P '\t-$' /tmp/access-check-rdns.txt | cut -f1 | sort -u > /tmp/access-check-nordns-ips.txt || true

# t.co経由を装う分散スキャナー群の目印(2026-09-22発見)。多数の別IPが揃って
# 同じ古いChromeのUAを名乗る。実在ユーザーの分布としては起こり得ない。
STALE_UA='Chrome/103\.0\.0\.0 Safari'
grep -E "$STALE_UA" /tmp/access-check-clean.log | awk '{print $1}' | sort -u > /tmp/access-check-staleua-ips.txt || true

cat /tmp/access-check-dc-ips.txt /tmp/access-check-staleua-ips.txt /tmp/access-check-nordns-ips.txt \
  | sort -u > /tmp/access-check-machine-ips.txt
if [ -s /tmp/access-check-machine-ips.txt ]; then
    grep -vFf <(sed 's/$/ /' /tmp/access-check-machine-ips.txt) /tmp/access-check-clean.log > /tmp/access-check-human.log || true
else
    cp /tmp/access-check-clean.log /tmp/access-check-human.log
fi

echo
echo "== さらにデータセンター由来を除外した「人間候補」 =="
echo "件数: $(wc -l < /tmp/access-check-human.log)"
echo "ユニークIP数: $(awk '{print $1}' /tmp/access-check-human.log | sort -u | wc -l)"
echo "  (除外内訳: 逆引きがホスティング事業者 $(wc -l < /tmp/access-check-dc-ips.txt) IP / 逆引きなし $(wc -l < /tmp/access-check-nordns-ips.txt) IP / 古いChromeのUA $(wc -l < /tmp/access-check-staleua-ips.txt) IP、重複を除いて $(wc -l < /tmp/access-check-machine-ips.txt) IP)"

echo
echo "== 人間候補として残ったIPと逆引き結果(判断の材料) =="
awk '{print $1}' /tmp/access-check-human.log | sort -u | while read -r ip; do
    printf '  %-16s %s\n' "$ip" "$(grep -P "^$ip\t" /tmp/access-check-rdns.txt | cut -f2)"
done

echo
echo "== ページ別の内訳(人間候補のみ、上位20) =="
awk '{print $7}' /tmp/access-check-human.log \
  | sed -E 's#/player/[^/? ]+#/player/{tag}#; s#/clan/[^/? ]+#/clan/{tag}#; s#/card/[^/? ]+#/card/{id}#; s#\?.*##' \
  | sort | uniq -c | sort -rn | head -20 || true

echo
echo "== ページ別の内訳(除外前、参考、上位20) =="
awk '{print $7}' /tmp/access-check-clean.log \
  | sed -E 's#/player/[^/? ]+#/player/{tag}#; s#/clan/[^/? ]+#/clan/{tag}#; s#/card/[^/? ]+#/card/{id}#; s#\?.*##' \
  | sort | uniq -c | sort -rn | head -20 || true

echo
echo "== 深いページ(player/clan/cards/ranking/favorites/card)へのアクセス =="
echo "(先頭の * はデータセンター由来として上で除外したもの)"
grep -E ' /(player|clan|cards|ranking|favorites|card)' /tmp/access-check-clean.log \
  | awk '{print $1, $4, $7}' \
  | while read -r ip rest; do
        if grep -qxF "$ip" /tmp/access-check-machine-ips.txt; then printf '* '; else printf '  '; fi
        echo "$ip $rest"
    done || echo "(なし)"

echo
echo "== X(Twitter)経由のリファラ =="
grep -iE 't\.co|x\.com' /tmp/access-check-clean.log || echo "(なし)"

# X等に出すURLには ?from=x を付けている(2026-09-21、フェーズ1.55)。
# リファラが付かない流入でもこれで数えられる。クエリの先頭(?from=)にも
# 2番目以降のパラメータ(&from=)にも付き得るため両方拾う。
echo
echo "== 流入元タグ(?from=)付きのアクセス =="
echo "-- 除外前(clean) --"
grep -ohE '[?&]from=[A-Za-z0-9_-]+' /tmp/access-check-clean.log | sed -E 's/^&/?/' | sort | uniq -c | sort -rn || echo "(なし)"
echo "-- 人間候補のみ --"
grep -ohE '[?&]from=[A-Za-z0-9_-]+' /tmp/access-check-human.log | sed -E 's/^&/?/' | sort | uniq -c | sort -rn || echo "(なし)"

# ビーコン(/beacon)はブラウザがJSを実行したときだけ叩かれる(2026-09-22追加、フェーズ1.61)。
# HTMLを取得するだけのクローラーはここに現れないため、ログのUA/IPによる推定と違って
# 「本当にブラウザで表示された回数」を直接数えられる。
# ユーザー本人の開発用IPや既知スキャナーの分まで数えると意味が無いので、ここでも
# known_noise.md のIPは落とす(ビーコン自体はJSを実行した本物のブラウザからしか来ないが、
# ユーザー本人の動作確認アクセスは「実訪問者」ではないため)。
# sendBeaconは必ずPOSTで送る。GETはクローラーがJS内のURLを拾って叩いたもの(GoogleOtherで実例あり)。
grep '"POST /beacon' /tmp/access-check-window.log > /tmp/access-check-beacon-all.log || true
grep -vE "^($NOISE_REGEX) " /tmp/access-check-beacon-all.log > /tmp/access-check-beacon.log || true

echo
echo "== 実ブラウザ表示(ビーコン /beacon) =="
# 除外後が0件でも「仕組みが動いていない」のか「本人の分しか無かった」のかを見分けられるよう、除外前の件数も出す。
echo "除外前(本人・既知ノイズ込み): $(wc -l < /tmp/access-check-beacon-all.log)件 / ユニーク$(awk '{print $1}' /tmp/access-check-beacon-all.log | sort -u | wc -l) IP"
if [ -s /tmp/access-check-beacon.log ]; then
    echo "件数: $(wc -l < /tmp/access-check-beacon.log)"
    echo "ユニークIP数: $(awk '{print $1}' /tmp/access-check-beacon.log | sort -u | wc -l)"
    # JSを実行するヘッドレスブラウザ型のボットもビーコンを送るため(Hetznerで実例あり)、
    # 送信元ごとに逆引きを出し、データセンター・スキャナー由来には * を付ける。
    echo "-- 送信元IP別(* はデータセンター・スキャナー由来) --"
    awk '{print $1}' /tmp/access-check-beacon.log | sort | uniq -c | while read -r cnt ip; do
        name=$(lookup "$ip") || true
        case "$name" in *' '*|''|*in-addr.arpa.) name='-' ;; esac
        if printf '%s' "$name" | grep -qP "$DC_REGEX|$SCANNER_REGEX"; then mark='*'; else mark=' '; fi
        printf '%s %3d %-16s %s\n' "$mark" "$cnt" "$ip" "$name"
    done
    echo "-- 表示されたページ別 --"
    cat /tmp/access-check-beacon.log \
      | grep -ohE 'p=[^& ]+' | sed 's/^p=//' \
      | sed -E 's#%2F#/#g; s#/player/[^/?]+#/player/{tag}#; s#/clan/[^/?]+#/clan/{tag}#; s#/card/[^/?]+#/card/{id}#' \
      | sort | uniq -c | sort -rn | head -20 || true
else
    if [ -s /tmp/access-check-beacon-all.log ]; then
        echo "除外後: 0件(本人・既知ノイズのIPからのビーコンしか無かった)"
    else
        echo "除外後: 0件(除外前も0件。ビーコンの仕組み自体が動いているか確認すること)"
    fi
fi
REMOTE
