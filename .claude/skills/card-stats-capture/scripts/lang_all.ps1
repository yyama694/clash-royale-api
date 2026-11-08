param([string]$Lang, [int]$LangY, [int]$LangWheel = 0, [string]$Root = "C:\dev\card-stats-shots\2026-11-08", [switch]$SkipSwitch, [int]$From = 0)
$ErrorActionPreference = "Stop"
$s = $PSScriptRoot
$sp = $PSScriptRoot
$d = "$Root\$Lang"
New-Item -ItemType Directory -Force $d | Out-Null
function Pid0 { return (Get-Process crosvm | Where-Object { $_.MainWindowTitle -match 'Clash Royale' }).Id }
function Click([int]$x, [int]$y, [int]$ms = 1500) { powershell -NoProfile -ExecutionPolicy Bypass -File "$s\click.ps1" -X $x -Y $y | Out-Null; Start-Sleep -Milliseconds $ms }
function Wheel([int]$n, [int]$ms = 2500) { powershell -NoProfile -ExecutionPolicy Bypass -File "$s\wheel.ps1" -Notches $n | Out-Null; Start-Sleep -Milliseconds $ms }
function Shot([string]$f) { powershell -NoProfile -ExecutionPolicy Bypass -File "$s\capture.ps1" -ProcessId (Pid0) -OutFile $f | Out-Null }
function Run($cards, [switch]$Champ) {
    foreach ($c in $cards) {
        if ($Champ) { & "$sp\hero_info.ps1" -X $c[0] -Y $c[1] -Name $c[2] -OutDir $d -Champion -Page3 }
        else { & "$sp\hero_info.ps1" -X $c[0] -Y $c[1] -Name $c[2] -OutDir $d }
        if ($LASTEXITCODE -ne 0) { Write-Output "FAILED at $($c[2])"; exit 1 }
    }
}

if (-not $SkipSwitch) {
    Click 390 995 2500
    Click 574 143 1500
    Click 400 615 2000
    Click 461 463 1800
    if ($LangWheel -ne 0) { Wheel $LangWheel 1500 }
    Shot "$d\00_langs.png"
    Click 343 $LangY 1500
    Shot "$d\00_confirm.png"
    Click 446 652 16000
    Shot "$d\00_home.png"
    Click 213 990 2000
    Click 460 143 2000
    Shot "$d\00_collection.png"
    # Stop here and look at 00_collection.png before going on: once (Italian) the cards tab opened in the deck editor
    # and the filter clicks below opened a deck card's Info/Remove menu instead.
    Write-Output "NAV DONE: check $d\00_collection.png, then run again with -SkipSwitch"
    exit 0
}
if ($From -le 0) {
    Click 367 347 1500
    Shot "$d\00_filter.png"
    Click 230 830 1200
    Click 590 600 1500
    Shot "$d\00_top.png"
}
if ($From -le 0) { Run @(,@(140, 520, "h18")) }
if ($From -le 1) {
    Wheel -12; Shot "$d\01_list.png"
    Run @(@(140,300,"h01"),@(275,300,"h02"),@(410,300,"h03"),@(545,300,"h04"),@(140,535,"h05"),@(275,535,"h06"),@(410,535,"h07"),@(545,535,"h08"))
}
if ($From -le 2) {
    Wheel -12; Shot "$d\02_list.png"
    Run @(@(140,300,"h09"),@(275,300,"h10"),@(410,300,"h11"),@(545,300,"h12"),@(140,535,"h13"),@(275,535,"h14"),@(410,535,"h15"),@(545,535,"h16"),@(140,770,"h17"))
}
if ($From -le 3) {
    Wheel -24 4500; Wheel 6; Shot "$d\03_list.png"
    Run @(@(140,340,"c01"),@(275,340,"c02"),@(410,340,"c03"),@(545,340,"c04"),@(140,575,"c05"),@(275,575,"c06"),@(410,575,"c07"),@(545,575,"c08")) -Champ
}
& "$sp\crop_sheet.ps1" -Files (1..18 | ForEach-Object { "$d\h{0:D2}_p1.png" -f $_ }) -OutFile "$d\crop_h.png" -AbY 715 -AbH 60
& "$sp\crop_sheet.ps1" -Files (1..8 | ForEach-Object { "$d\c{0:D2}_p3.png" -f $_ }) -OutFile "$d\crop_c.png" -AbY 535 -AbH 50
Write-Output "ALL DONE $Lang"
