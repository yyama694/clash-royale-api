#!/bin/bash
# ランク戦 世界トップ1000人の国籍内訳を集計する。
# 使い方: bash collect.sh <作業ディレクトリ> [国コード一覧ファイル]
#   作業ディレクトリ … 中間ファイルと結果を置く場所(セッションのscratchpad推奨)
#   国コード一覧      … 省略時はサイトから全254カ国を取得する。スモークテスト時に絞り込み用
# 結果: <作業ディレクトリ>/country_counts.tsv  (国コード / トップ1000人中の人数 / その国の総数)
set -u

BASE="https://princess-tower.duckdns.org"
WORK="${1:?作業ディレクトリを指定してください}"
CC_LIST="${2:-}"
SLEEP="${SLEEP:-0.4}"   # VM負荷対策。短くしすぎない

mkdir -p "$WORK/cc" || exit 1
cd "$WORK" || exit 1

echo "[1/4] グローバル上位1000人のタグを取得"
curl -s --max-time 60 "$BASE/ranking/players/table" -o global.html
grep -oE 'href="/player/[0-9A-Z]+"' global.html | sed 's|href="/player/||; s|"||' | sort -u > global_sorted.txt
GLOBAL_N=$(wc -l < global_sorted.txt)
echo "      グローバル: ${GLOBAL_N}人"
if [ "$GLOBAL_N" -lt 100 ]; then
  echo "      ⚠ 取得できていません。中止します" >&2
  exit 1
fi

echo "[2/4] 国コードと国名(日本語)を取得"
if [ -z "$CC_LIST" ]; then
  curl -s --max-time 60 "$BASE/ranking/players?lang=en" -o rp_en.html
  grep -oE 'value="[A-Z]{2}"' rp_en.html | sed 's/value="//; s/"//' | sort -u > countries.txt
  CC_LIST="countries.txt"
fi
curl -s --max-time 60 "$BASE/ranking/players?lang=ja" -o rp_ja.html
grep -oE '<option value="[A-Z]{2}"[^>]*>[^<]+</option>' rp_ja.html \
  | sed -E 's/<option value="([A-Z]{2})"[^>]*>([^<]+)<\/option>/\1\t\2/' | sort -u > names_ja.tsv
echo "      対象: $(wc -l < "$CC_LIST")カ国 / 国名辞書: $(wc -l < names_ja.tsv)件"

echo "[3/4] 各国ランキングを取得して突き合わせ(${SLEEP}秒間隔)"
: > country_counts.tsv
: > fetch_errors.txt
while read -r CC; do
  [ -z "$CC" ] && continue
  curl -s --max-time 30 "$BASE/ranking/players/table?country=$CC" -o "cc/$CC.html"
  if [ ! -s "cc/$CC.html" ]; then
    echo "$CC EMPTY" >> fetch_errors.txt
    sleep "$SLEEP"
    continue
  fi
  grep -oE 'href="/player/[0-9A-Z]+"' "cc/$CC.html" | sed 's|href="/player/||; s|"||' | sort -u > "cc/$CC.tags"
  hit=$(comm -12 "cc/$CC.tags" global_sorted.txt | wc -l)
  total=$(wc -l < "cc/$CC.tags")
  printf '%s\t%s\t%s\n' "$CC" "$hit" "$total" >> country_counts.tsv
  rm -f "cc/$CC.html"
  sleep "$SLEEP"
done < "$CC_LIST"

echo "[4/4] 検証と結果"
for f in cc/*.tags; do comm -12 "$f" global_sorted.txt; done | sort > matched.txt
MATCHED=$(wc -l < matched.txt)
UNIQ=$(sort -u matched.txt | wc -l)
DUP=$(uniq -d matched.txt | wc -l)
MISS=$(comm -23 global_sorted.txt <(sort -u matched.txt) | wc -l)
echo "=== 検証(全カ国を対象にした場合、上3つが${GLOBAL_N}・${GLOBAL_N}・0、最後が0なら完全) ==="
echo "  マッチ総数: $MATCHED / ユニーク: $UNIQ / 2カ国以上に出た: $DUP / どの国にも出なかった: $MISS"
[ -s fetch_errors.txt ] && echo "  ⚠ 取得失敗: $(wc -l < fetch_errors.txt)件 (fetch_errors.txt)"

echo "=== 上位20カ国 ==="
sort -t$'\t' -k2,2nr country_counts.tsv | head -20 | while IFS=$'\t' read -r cc hit total; do
  name=$(awk -F'\t' -v c="$cc" '$1==c{print $2}' names_ja.tsv)
  printf '  %-3s %-12s %4s人  (その国の登録総数 %s)\n' "$cc" "$name" "$hit" "$total"
done
echo "=== サマリ ==="
echo "  1人以上いる国: $(awk -F'\t' '$2>0' country_counts.tsv | wc -l)カ国 / 調査対象 $(wc -l < country_counts.tsv)カ国"
echo "  上位10カ国の合計: $(sort -t$'\t' -k2,2nr country_counts.tsv | head -10 | awk -F'\t' '{s+=$2} END{print s}')人"
