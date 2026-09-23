#!/bin/bash
# ユーザタグ収集の巡回バッチ(クラン/プレイヤーのクロール、名前検索インデックスの整理)の稼働状況をVMで確認する。
set -euo pipefail

SSH_KEY="C:\\Users\\Norio Fukuchi\\.ssh\\oci_clash_royale_api"
VM_HOST="opc@161.33.136.175"
SSH_OPTS=(-i "$SSH_KEY" -o ConnectTimeout=10)

ssh "${SSH_OPTS[@]}" "$VM_HOST" '
IDX=/var/lib/clash-royale-api/player-index

echo "=== サービス状態・設定 ==="
sudo systemctl is-active clash-royale-api
sudo systemctl show clash-royale-api --property=ActiveEnterTimestamp
sudo grep -E "CRAWLER|PLAYER_NAME" /etc/clash-royale-api/env

echo
echo "=== 保存済みタグ数(by-tag全ファイルの行数合計。ファイル数ではない) ==="
sudo bash -c "cat $IDX/by-tag/*.tsv 2>/dev/null | wc -l"

echo
echo "=== inbox未整理行数(巡回で見つけたがまだ反映前) ==="
sudo bash -c "cat $IDX/inbox/*.tsv 2>/dev/null | wc -l"

echo
echo "=== 直近のクロール進捗ログ ==="
sudo journalctl -u clash-royale-api --since "3 hours ago" --no-pager | grep "crawler:" | tail -5

echo
echo "=== 直近1時間のクロール関連エラー ==="
sudo journalctl -u clash-royale-api --since "1 hour ago" --no-pager | grep -i "crawl" | grep -iE "error|exception" | tail -10
echo "(上が空なら該当なし)"

echo
echo "=== 現在時刻(UTC) ==="
date -u
'
