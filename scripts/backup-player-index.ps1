<#
.SYNOPSIS
    本番VMのプレイヤー名索引・記録・設定をこのPCにバックアップする。

.DESCRIPTION
    VM上でtar.gzを作ってscpで取得し、SHA256で転送を検証したうえでVM側の一時ファイルを消す。
    取得するのは2つ。
    - player-index-*.tar.gz: by-tag(正本)・crawler(巡回の進捗)・ranking-history(ランキングの動き画面の記録)・
      card-usage(デッキ集計とその履歴)。ranking-history と card-usage の履歴は、公式APIが今のランキングと
      直近約30戦しか返さないため作り直せない。by-name は PlayerIndexCompactor が無ければ起動後に自動で
      作り直すため、inbox は毎時の整理バッチで消える一時ファイルのため除く。
    - vm-config-*.tar.gz: systemd・Apache・logrotate・cron・firewalld の設定、自作スクリプト、日次アクセス数のログ。

    成否は $BackupDir\backup.log に1行ずつ追記する(タスクスケジューラからの無人実行用)。
    定期実行の登録は scripts\register-backup-task.ps1 で行う。

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
$Targets        = 'by-tag crawler ranking-history card-usage'
# APIトークン(/etc/clash-royale-api/env)と証明書の秘密鍵は含めない。トークンは開発者サイトで、証明書は
# certbotで作り直せるので、秘密情報をPCに置く場所を増やさない。
$VmConfigPaths  = 'etc/systemd/system/clash-royale-api.service ' +
                  'etc/httpd/conf.d/princess-tower-proxy.conf etc/httpd/conf.d/princess-tower-proxy-le-ssl.conf ' +
                  'etc/httpd/conf.d/block-scrapers.conf ' +
                  'etc/logrotate.d/httpd etc/firewalld/zones ' +
                  'etc/cron.d/clash-royale-access-stats etc/cron.d/clash-royale-daily-backup ' +
                  'usr/local/bin/clash-royale-access-stats.sh usr/local/bin/clash-royale-daily-backup.sh ' +
                  'var/log/clash-royale-api/daily-access-stats.log'

function Write-Log([string]$Message) {
    if (-not (Test-Path $BackupDir)) { New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null }
    $line = '{0} {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message
    Add-Content -Path (Join-Path $BackupDir 'backup.log') -Value $line -Encoding UTF8
    Write-Host $line
}

# タスクスケジューラからの無人実行は画面に何も残らないため、成否を必ずログに1行残す。
trap {
    Write-Log "NG  $($_.Exception.Message)"
    break
}

function Invoke-Ssh([string]$Command) {
    $out = & ssh -i $KeyPath -o ConnectTimeout=20 -o BatchMode=yes $VmHost $Command
    if ($LASTEXITCODE -ne 0) { throw "SSHコマンドが失敗しました (exit $LASTEXITCODE): $Command" }
    return $out
}

# $TarCommand の {0} にVM上の一時ファイルのパスを入れて実行し、取得してSHA256で検証する。戻り値は (サイズ, SHA256)。
function Get-RemoteArchive([string]$TarCommand, [string]$FileName) {
    $remoteTmp = "/tmp/$FileName"
    $localPath = Join-Path $BackupDir $FileName
    try {
        Write-Host "      VM上で圧縮中..."
        # 巡回が discovered.txt に追記し続けるため tar が exit 1(警告)で終わることがある。末尾が切れても
        # TagQueue は行単位で読んで壊れた行を捨てるだけなので許容する。exit 2 以上は本当の失敗。
        & ssh -i $KeyPath -o ConnectTimeout=20 -o BatchMode=yes $VmHost ($TarCommand -f $remoteTmp)
        $tarExit = $LASTEXITCODE
        if ($tarExit -gt 1) { throw "tarが失敗しました (exit $tarExit)" }
        if ($tarExit -eq 1) { Write-Host "      (巡回中のファイルが更新されましたが、想定内なので続行します)" }

        $remoteInfo   = Invoke-Ssh "stat -c %s $remoteTmp; sha256sum $remoteTmp | cut -d' ' -f1"
        $remoteSize   = [int64]$remoteInfo[0]
        $remoteSha256 = $remoteInfo[1]

        Write-Host ("      ダウンロード中 ({0:N1} MB)..." -f ($remoteSize / 1MB))
        & scp -i $KeyPath -o ConnectTimeout=20 -q "${VmHost}:${remoteTmp}" $localPath
        if ($LASTEXITCODE -ne 0) { throw "scpが失敗しました (exit $LASTEXITCODE)" }

        $localSize   = (Get-Item $localPath).Length
        $localSha256 = (Get-FileHash -Path $localPath -Algorithm SHA256).Hash.ToLower()
        if ($localSize -ne $remoteSize) { throw "サイズが一致しません (VM: $remoteSize / ローカル: $localSize)" }
        if ($localSha256 -ne $remoteSha256) { throw "SHA256が一致しません。転送が壊れています" }
        Write-Host "      OK (SHA256: $localSha256)"
        return @($localSize, $localSha256)
    }
    catch {
        if (Test-Path $localPath) { Remove-Item $localPath -Force }
        throw
    }
    finally {
        & ssh -i $KeyPath -o ConnectTimeout=20 -o BatchMode=yes $VmHost "rm -f $remoteTmp" 2>&1 | Out-Null
    }
}

function Remove-OldGenerations([string]$Prefix) {
    Get-ChildItem -Path $BackupDir -Filter "$Prefix-*.tar.gz" |
        Sort-Object Name -Descending |
        Select-Object -Skip $Keep |
        ForEach-Object {
            Remove-Item $_.FullName -Force
            Write-Host "      削除: $($_.Name)"
        }
}

foreach ($exe in 'ssh', 'scp') {
    if (-not (Get-Command $exe -ErrorAction SilentlyContinue)) {
        throw "$exe が見つかりません。Windowsの OpenSSH クライアントが入っているか確認してください"
    }
}
if (-not (Test-Path $KeyPath)) { throw "SSH鍵が見つかりません: $KeyPath" }
if (-not (Test-Path $BackupDir)) { New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null }

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'

Write-Host "[1/3] 索引と記録 ($Targets)"
$indexFile = "player-index-$stamp.tar.gz"
$index = Get-RemoteArchive "tar czf {0} -C $RemoteIndexDir $Targets" $indexFile

Write-Host "[2/3] VMの設定"
$configFile = "vm-config-$stamp.tar.gz"
# 一部はrootしか読めないのでsudoで固め、scpで取れるようopcに戻す。まだ無いファイルがあっても止めない。
$config = Get-RemoteArchive "cd / && sudo tar czf {0} --ignore-failed-read $VmConfigPaths && sudo chown opc:opc {0}" $configFile

Write-Host "[3/3] 古い世代を整理中 (最新 $Keep 件を残す)..."
Remove-OldGenerations 'player-index'
Remove-OldGenerations 'vm-config'

Write-Host ""
Write-Log ("OK  {0}  {1:N1} MB  sha256={2}" -f $indexFile, ($index[0] / 1MB), $index[1])
Write-Log ("OK  {0}  {1:N1} KB  sha256={2}" -f $configFile, ($config[0] / 1KB), $config[1])
Get-ChildItem -Path $BackupDir -Filter '*.tar.gz' |
    Sort-Object Name -Descending |
    Select-Object Name, @{n='Size(MB)';e={[math]::Round($_.Length / 1MB, 1)}}, LastWriteTime |
    Format-Table -AutoSize
