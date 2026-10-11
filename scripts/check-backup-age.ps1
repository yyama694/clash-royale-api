<#
.SYNOPSIS
    週1回のバックアップが滞っていたら警告する(Claude CodeのSessionStartフックから呼ぶ)。

.DESCRIPTION
    backup.log の最後の成功が8日より前、または最後の行が失敗なら、警告をJSONで出す。問題が無ければ何も出さない。
    バックアップはPCが起きているときしか動かないため、気づける場所としてClaude Codeの起動時を使っている。
#>
param(
    [string]$BackupDir = 'C:\dev\backup\clash-royale-api',
    [int]$MaxAgeDays = 8
)

# Windows PowerShell 5.1 の標準出力はCP932なので、Claude CodeがUTF-8で読めるようにする。
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$log = Join-Path $BackupDir 'backup.log'
$problem = $null
if (-not (Test-Path $log)) {
    $problem = "バックアップのログ($log)がありません"
} else {
    $lines = Get-Content $log -Encoding UTF8 | Where-Object { $_.Trim() }
    $lastOk = $lines | Where-Object { $_ -match '^\S+ \S+ OK  player-index-' } | Select-Object -Last 1
    $last = $lines | Select-Object -Last 1
    if (-not $lastOk) {
        $problem = 'バックアップが一度も成功していません'
    } else {
        $okAt = [datetime]::ParseExact(($lastOk.Substring(0, 19)), 'yyyy-MM-dd HH:mm:ss', $null)
        $days = ((Get-Date) - $okAt).TotalDays
        if ($days -gt $MaxAgeDays) {
            $problem = '最後に成功したバックアップは{0:yyyy-MM-dd HH:mm}({1:N0}日前)です' -f $okAt, [math]::Floor($days)
        } elseif ($last -match ' NG  ') {
            $problem = "直近のバックアップが失敗しています: $last"
        }
    }
}

if ($problem) {
    $message = "⚠ 本番VMのバックアップ: $problem。scripts\backup-player-index.ps1 を実行してください。"
    @{
        systemMessage      = $message
        hookSpecificOutput = @{ hookEventName = 'SessionStart'; additionalContext = "$message ユーザーに伝えること。" }
    } | ConvertTo-Json -Compress
}
