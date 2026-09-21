<#
.SYNOPSIS
    プレイヤー名索引のバックアップを、Windowsタスクスケジューラに定期実行として登録する。

.DESCRIPTION
    同名のタスクが既にあれば置き換える。管理者権限は不要で、ログオン中のユーザーとして動く。
    PCが落ちていて実行時刻を逃した場合は、次に使えるようになった時点で取り返す(StartWhenAvailable)。

    解除するとき:
        Unregister-ScheduledTask -TaskName 'clash-royale-api-backup' -Confirm:$false

    結果の確認:
        Get-Content C:\dev\backup\clash-royale-api\backup.log -Tail 10

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\register-backup-task.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\register-backup-task.ps1 -DayOfWeek Saturday -At 22:00
#>
param(
    [string]$TaskName = 'clash-royale-api-backup',
    [ValidateSet('Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday')]
    [string]$DayOfWeek = 'Sunday',
    [string]$At = '12:00',
    [string]$BackupDir = 'C:\dev\backup\clash-royale-api',
    [int]$Keep = 3
)

$ErrorActionPreference = 'Stop'

$scriptPath = Join-Path $PSScriptRoot 'backup-player-index.ps1'
if (-not (Test-Path $scriptPath)) { throw "バックアップスクリプトが見つかりません: $scriptPath" }

$arguments = '-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "{0}" -BackupDir "{1}" -Keep {2}' -f $scriptPath, $BackupDir, $Keep

$action  = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $arguments
$trigger = New-ScheduledTaskTrigger -Weekly -DaysOfWeek $DayOfWeek -At $At
# 取得は数分で終わるので、異常に長引いたら1時間で打ち切る。
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Hours 1) -DontStopOnIdleEnd

if (Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue) {
    Write-Host "既存のタスクを置き換えます: $TaskName"
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
}

$description = 'Clash Royale APIのプレイヤー名索引(by-tag/crawler)を本番VMからこのPCへバックアップする'
Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Settings $settings -Description $description | Out-Null

Write-Host "登録しました: $TaskName (毎週$DayOfWeek $At)"
Get-ScheduledTask -TaskName $TaskName | Get-ScheduledTaskInfo | Select-Object TaskName, NextRunTime, LastRunTime, LastTaskResult | Format-List
