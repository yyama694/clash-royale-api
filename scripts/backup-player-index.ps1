<#
.SYNOPSIS
    本番VMのプレイヤー名索引をこのPCにバックアップする。

.DESCRIPTION
    VM上でtar.gzを作ってscpで取得し、SHA256で転送を検証したうえでVM側の一時ファイルを消す。
    取得対象は by-tag(正本)と crawler(巡回の進捗)だけ。by-name は PlayerIndexCompactor が
    無ければ起動後に自動で作り直すため、inbox は毎時の整理バッチで消える一時ファイルのため除く。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\backup-player-index.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\backup-player-index.ps1 -BackupDir D:\backup -Keep 5
#>
param(
    [string]$BackupDir = 'C:\dev\backup\clash-royale-api',
    [int]$Keep = 3
)

$ErrorActionPreference = 'Stop'

$VmHost         = 'opc@161.33.136.175'
$KeyPath        = Join-Path $env:USERPROFILE '.ssh\oci_clash_royale_api'
$RemoteIndexDir = '/var/lib/clash-royale-api/player-index'
$Targets        = 'by-tag crawler'

function Invoke-Ssh([string]$Command) {
    $out = & ssh -i $KeyPath -o ConnectTimeout=20 -o BatchMode=yes $VmHost $Command
    if ($LASTEXITCODE -ne 0) { throw "SSHコマンドが失敗しました (exit $LASTEXITCODE): $Command" }
    return $out
}

if (-not (Test-Path $KeyPath)) { throw "SSH鍵が見つかりません: $KeyPath" }
if (-not (Test-Path $BackupDir)) { New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null }

$stamp     = Get-Date -Format 'yyyyMMdd-HHmmss'
$fileName  = "player-index-$stamp.tar.gz"
$remoteTmp = "/tmp/$fileName"
$localPath = Join-Path $BackupDir $fileName

try {
    Write-Host "[1/5] VM上で圧縮中 ($Targets)..."
    # 巡回が discovered.txt に追記し続けるため tar が exit 1(警告)で終わることがある。末尾が切れても
    # TagQueue は行単位で読んで壊れた行を捨てるだけなので許容する。exit 2 以上は本当の失敗。
    & ssh -i $KeyPath -o ConnectTimeout=20 -o BatchMode=yes $VmHost "tar czf $remoteTmp -C $RemoteIndexDir $Targets"
    $tarExit = $LASTEXITCODE
    if ($tarExit -gt 1) { throw "tarが失敗しました (exit $tarExit)" }
    if ($tarExit -eq 1) { Write-Host "      (巡回中のファイルが更新されましたが、想定内なので続行します)" }

    Write-Host "[2/5] 転送前のサイズとSHA256を取得中..."
    $remoteInfo   = Invoke-Ssh "stat -c %s $remoteTmp; sha256sum $remoteTmp | cut -d' ' -f1"
    $remoteSize   = [int64]$remoteInfo[0]
    $remoteSha256 = $remoteInfo[1]
    Write-Host ("      {0:N1} MB" -f ($remoteSize / 1MB))

    Write-Host "[3/5] ダウンロード中..."
    & scp -i $KeyPath -o ConnectTimeout=20 -q "${VmHost}:${remoteTmp}" $localPath
    if ($LASTEXITCODE -ne 0) { throw "scpが失敗しました (exit $LASTEXITCODE)" }

    Write-Host "[4/5] 検証中..."
    $localSize   = (Get-Item $localPath).Length
    $localSha256 = (Get-FileHash -Path $localPath -Algorithm SHA256).Hash.ToLower()
    if ($localSize -ne $remoteSize) { throw "サイズが一致しません (VM: $remoteSize / ローカル: $localSize)" }
    if ($localSha256 -ne $remoteSha256) { throw "SHA256が一致しません。転送が壊れています" }
    Write-Host "      OK (SHA256: $localSha256)"
}
catch {
    if (Test-Path $localPath) { Remove-Item $localPath -Force }
    throw
}
finally {
    & ssh -i $KeyPath -o ConnectTimeout=20 -o BatchMode=yes $VmHost "rm -f $remoteTmp" 2>&1 | Out-Null
}

Write-Host "[5/5] 古い世代を整理中 (最新 $Keep 件を残す)..."
$old = Get-ChildItem -Path $BackupDir -Filter 'player-index-*.tar.gz' |
       Sort-Object Name -Descending |
       Select-Object -Skip $Keep
foreach ($f in $old) {
    Remove-Item $f.FullName -Force
    Write-Host "      削除: $($f.Name)"
}

Write-Host ""
Write-Host "完了: $localPath"
Get-ChildItem -Path $BackupDir -Filter 'player-index-*.tar.gz' |
    Sort-Object Name -Descending |
    Select-Object Name, @{n='Size(MB)';e={[math]::Round($_.Length / 1MB, 1)}}, LastWriteTime |
    Format-Table -AutoSize
