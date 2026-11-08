param([int]$X, [int]$Y, [string]$Name, [string]$OutDir = "C:\dev\card-stats-shots\2026-11-08", [switch]$NoClose, [switch]$Champion, [switch]$Page3, [int]$Back = 3)
$ErrorActionPreference = "Stop"
$s = $PSScriptRoot
$p = (Get-Process crosvm | Where-Object { $_.MainWindowTitle -match 'Clash Royale' }).Id
Add-Type -AssemblyName System.Drawing

function Shot([string]$f) {
    powershell -NoProfile -ExecutionPolicy Bypass -File "$s\capture.ps1" -ProcessId $p -OutFile $f | Out-Null
    return [System.Drawing.Bitmap]::FromFile($f)
}
function Click([int]$cx, [int]$cy) {
    powershell -NoProfile -ExecutionPolicy Bypass -File "$s\click.ps1" -X $cx -Y $cy | Out-Null
}
function IsBlue($c) { return ($c.B -gt 190 -and $c.R -lt 130 -and $c.G -gt 130 -and $c.G -lt 200) }
function IsYellow($c) { return ($c.R -gt 200 -and $c.G -gt 140 -and $c.G -lt 222 -and $c.B -lt 90) }
function IsRed($c) { return ($c.R -gt 200 -and $c.G -lt 160 -and $c.B -lt 160) }
function IsHeroIcon($c) { return ($c.R -gt 180 -and $c.G -gt 90 -and $c.G -lt 140 -and $c.B -lt 40) }

Click $X $Y
Start-Sleep -Milliseconds 1300
# menu = a blue/green button (>=15px tall) followed within 45px by a yellow button (>=15px tall)
function FindMenu($bmp) {
$infoY = -1
$cls = @{}
for ($yy = 150; $yy -lt 930; $yy++) {
    $cls[$yy] = "."
    foreach ($dx in @(-60, -52, -45, -38)) {
        $c = $bmp.GetPixel($X + $dx, $yy)
        if ((IsBlue $c) -or ($c.G -gt 200 -and $c.R -lt 150 -and $c.B -lt 150)) { $cls[$yy] = "b"; break }
        elseif (IsYellow $c) { $cls[$yy] = "y"; break }
    }
}
$rt = New-Object System.Collections.Generic.List[string]
$rs = New-Object System.Collections.Generic.List[int]
$re = New-Object System.Collections.Generic.List[int]
for ($yy = 150; $yy -lt 930; $yy++) {
    if ($rt.Count -eq 0 -or $rt[$rt.Count - 1] -ne $cls[$yy]) { $rt.Add($cls[$yy]); $rs.Add($yy); $re.Add($yy) }
    else { $re[$re.Count - 1] = $yy }
}
for ($i = 0; $i -lt $rt.Count; $i++) {
    if ($rt[$i] -ne "b" -or ($re[$i] - $rs[$i]) -lt 15) { continue }
    for ($j = $i + 1; $j -lt $rt.Count; $j++) {
        if ($rs[$j] - $re[$i] -gt 45) { break }
        if ($rt[$j] -eq "y" -and ($re[$j] - $rs[$j]) -ge 15) { $infoY = [int](($rs[$i] + $re[$i]) / 2); break }
    }
    if ($infoY -ge 0) { break }
}
return $infoY
}
# a capture taken mid-redraw can come out partly red, so look again on a fresh capture before giving up
for ($try = 0; $try -lt 3; $try++) {
    $bmp = Shot "$OutDir\${Name}_menu.png"
    $infoY = FindMenu $bmp
    $bmp.Dispose()
    if ($infoY -ge 0) { break }
    Start-Sleep -Milliseconds 1500
}
if ($infoY -lt 0) { Write-Output "NO_MENU"; exit 1 }
Click $X $infoY
Start-Sleep -Milliseconds 2800
function State([string]$f) {
    $b = Shot $f
    $tall = (IsRed ($b.GetPixel(553, 87))) -or (IsRed ($b.GetPixel(579, 87)))
    $short = (IsRed ($b.GetPixel(553, 323))) -or (IsRed ($b.GetPixel(579, 323)))
    $b.Dispose()
    if ($tall) { return "tall" } elseif ($short) { return "short" } else { return "none" }
}
$st = State "$OutDir\${Name}_opened.png"
if ($st -eq "none") { Start-Sleep -Milliseconds 1500; $st = State "$OutDir\${Name}_opened.png" }
if ($st -eq "none") { Write-Output "NOT_OPEN infoY=$infoY"; exit 1 }
$tabX = 420
if ($Champion) { if ($st -ne "tall") { Write-Output "CHAMPION_NOT_TALL"; exit 1 } }
elseif ($st -eq "short") {
    # a capture taken mid-redraw can come out partly red, so look again on a fresh capture before giving up
    for ($try = 0; $try -lt 3; $try++) {
        $b = if ($try -eq 0) { [System.Drawing.Bitmap]::FromFile("$OutDir\${Name}_opened.png") } else { Shot "$OutDir\${Name}_opened.png" }
        if (IsHeroIcon ($b.GetPixel(488, 982))) { $tabX = 488 } elseif (IsHeroIcon ($b.GetPixel(420, 982))) { $tabX = 420 } else { $tabX = -1 }
        $b.Dispose()
        if ($tabX -ge 0) { break }
        Start-Sleep -Milliseconds 1500
    }
    if ($tabX -lt 0) { Write-Output "NO_HERO_TAB"; exit 1 }
    Click $tabX 982
    Start-Sleep -Milliseconds 2800
    # the hero's 3D model (balloon etc.) can cover the close button for a moment while it animates
    for ($try = 0; $try -lt 4; $try++) {
        $st = State "$OutDir\${Name}_tab.png"
        if ($st -eq "tall") { break }
        Start-Sleep -Milliseconds 1500
    }
    if ($st -ne "tall") { Write-Output "HERO_TAB_FAILED"; exit 1 }
}
for ($i = 0; $i -lt $Back; $i++) {
    powershell -NoProfile -ExecutionPolicy Bypass -File "$s\click.ps1" -X 160 -Y 660 -ToX 520 -ToY 660 | Out-Null
    Start-Sleep -Milliseconds 1500
}
$st = State "$OutDir\${Name}_p1.png"
if ($st -ne "tall") { Write-Output "LOST_DIALOG"; exit 1 }
if ($Page3) {
    for ($i = 0; $i -lt 2; $i++) {
        powershell -NoProfile -ExecutionPolicy Bypass -File "$s\click.ps1" -X 520 -Y 660 -ToX 160 -ToY 660 | Out-Null
        Start-Sleep -Milliseconds 1500
    }
    $st = State "$OutDir\${Name}_p3.png"
}
if ($NoClose) { Write-Output "OPEN infoY=$infoY"; exit 0 }
Click 566 87
Start-Sleep -Milliseconds 1500
$bmp = Shot "$OutDir\${Name}_closed.png"
$still = (IsRed ($bmp.GetPixel(553, 87))) -or (IsRed ($bmp.GetPixel(579, 87)))
$bmp.Dispose()
if ($still) { Write-Output "STILL_OPEN"; exit 1 }
Click $X ($infoY - 123)
Start-Sleep -Milliseconds 1200
Write-Output "DONE infoY=$infoY"
