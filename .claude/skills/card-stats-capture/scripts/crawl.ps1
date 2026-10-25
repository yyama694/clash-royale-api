param(
  [Parameter(Mandatory)][string]$Cards,   # "x:y;x:y;..." card centers in the collection grid (a comma would be split by powershell -File)
  [Parameter(Mandatory)][string]$OutDir,
  [int]$StartIndex = 1,
  [string]$ProbeImage = ''   # only print what FindMenu sees at each card point in this saved image, then exit
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class K {
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint flags);
  [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint m, IntPtr w, IntPtr l);
}
"@
[K]::SetProcessDPIAware() | Out-Null
New-Item -ItemType Directory -Force $OutDir | Out-Null

$proc = Get-Process crosvm | Where-Object { $_.MainWindowTitle -match 'Clash Royale' } | Select-Object -First 1
if (-not $proc) { throw "Clash Royale window not found" }
$H = $proc.MainWindowHandle

function Grab {
  $r = New-Object K+RECT
  [K]::GetClientRect($H, [ref]$r) | Out-Null
  if (($r.R - $r.L) -ne 559 -or ($r.B - $r.T) -ne 993) { throw "unexpected client size $($r.R - $r.L)x$($r.B - $r.T); coordinates assume 559x993" }
  $bmp = New-Object System.Drawing.Bitmap 559, 993
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $hdc = $g.GetHdc(); [K]::PrintWindow($H, $hdc, 3) | Out-Null; $g.ReleaseHdc($hdc); $g.Dispose()
  return $bmp
}
function MakeLParam([int]$x, [int]$y) { [IntPtr](($y -shl 16) -bor ($x -band 0xFFFF)) }
function Click([int]$x, [int]$y) {
  [K]::PostMessage($H, 0x0200, [IntPtr]0, (MakeLParam $x $y)) | Out-Null
  [K]::PostMessage($H, 0x0201, [IntPtr]1, (MakeLParam $x $y)) | Out-Null
  Start-Sleep -Milliseconds 80
  [K]::PostMessage($H, 0x0202, [IntPtr]0, (MakeLParam $x $y)) | Out-Null
}
function Drag([int]$x1, [int]$y1, [int]$x2, [int]$y2, [int]$steps = 15, [int]$stepMs = 15, [int]$holdMs = 80) {
  [K]::PostMessage($H, 0x0200, [IntPtr]0, (MakeLParam $x1 $y1)) | Out-Null
  [K]::PostMessage($H, 0x0201, [IntPtr]1, (MakeLParam $x1 $y1)) | Out-Null
  for ($i = 1; $i -le $steps; $i++) {
    Start-Sleep -Milliseconds $stepMs
    [K]::PostMessage($H, 0x0200, [IntPtr]1, (MakeLParam ([int]($x1 + ($x2 - $x1) * $i / $steps)) ([int]($y1 + ($y2 - $y1) * $i / $steps)))) | Out-Null
  }
  Start-Sleep -Milliseconds $holdMs
  [K]::PostMessage($H, 0x0202, [IntPtr]0, (MakeLParam $x2 $y2)) | Out-Null
}

function IsRed($c) { $c.R -gt 200 -and $c.G -lt 130 -and $c.B -lt 130 }
# The info button is a gradient: about 89,180,224 at the top down to 54,139,222 at the bottom
function IsBlue($c) { $c.B -gt 200 -and $c.R -lt 110 -and $c.G -ge 120 -and $c.G -le 195 }
function IsYellow($c) { $c.R -gt 190 -and $c.G -gt 130 -and $c.G -lt 200 -and $c.B -lt 80 }
function IsPurple($c) { $c.R -ge 50 -and $c.R -le 120 -and $c.G -lt 40 -and $c.B -gt 130 }
function IsOrange($c) { $c.R -gt 150 -and $c.G -ge 60 -and $c.G -le 140 -and $c.B -lt 50 }

# The close button is a white X on red; red shows 13px to either side of its center
function DialogKind($bmp) {
  if ((IsRed ($bmp.GetPixel(487, 287))) -and (IsRed ($bmp.GetPixel(513, 287)))) { return 'normal' }
  if ((IsRed ($bmp.GetPixel(487, 47))) -and (IsRed ($bmp.GetPixel(513, 47)))) { return 'hero' }
  return $null
}
function CloseXY($kind) { if ($kind -eq 'hero') { 500, 47 } else { 500, 287 } }

function AnyInRow($bmp, $x0, $x1, $test) {
  for ($y = 935; $y -le 955; $y += 2) { for ($x = $x0; $x -le $x1; $x++) { if (& $test ($bmp.GetPixel($x, $y))) { return $true } } }
  return $false
}
function Tabs($bmp) {
  if ((AnyInRow $bmp 255 290 ${function:IsPurple}) -and (AnyInRow $bmp 405 440 ${function:IsOrange})) { return @(@{x=124;t='n'}, @{x=272;t='e'}, @{x=422;t='h'}) }
  if (AnyInRow $bmp 335 370 ${function:IsPurple}) { return @(@{x=195;t='n'}, @{x=352;t='e'}) }
  if (AnyInRow $bmp 335 370 ${function:IsOrange}) { return @(@{x=195;t='n'}, @{x=352;t='h'}) }
  return @(@{x=-1;t='n'})
}

function Signature($bmp, $x0, $x1, $y0, $y1) {
  $s = New-Object double[] 800
  for ($j = 0; $j -lt 20; $j++) { for ($i = 0; $i -lt 40; $i++) {
    $c = $bmp.GetPixel($x0 + [int](($x1 - $x0) * $i / 40), $y0 + [int](($y1 - $y0) * $j / 20))
    $s[$j * 40 + $i] = 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B } }
  return ,$s
}
function SigDiff($a, $b) { $t = 0.0; for ($k = 0; $k -lt $a.Length; $k++) { $t += [Math]::Abs($a[$k] - $b[$k]) }; $t / $a.Length }

function Fail($msg, $bmp) {
  $f = Join-Path $OutDir ("error_{0}.png" -f (Get-Date -Format 'HHmmss'))
  if ($bmp) { $bmp.Save($f, [System.Drawing.Imaging.ImageFormat]::Png) }
  throw "STOP: $msg (screen saved to $f)"
}
function RequireDialog {
  $b = Grab; $k = DialogKind $b
  if (-not $k) { Fail "info dialog is not open" $b }
  $b.Dispose(); return $k
}
function Save($bmp, $name) { $bmp.Save((Join-Path $OutDir $name), [System.Drawing.Imaging.ImageFormat]::Png) }

function IsGreen($c) { $c.G -gt 170 -and $c.R -lt 120 -and $c.B -lt 120 }

# The card's menu: the yellow "use" button, and above it either the blue "info" button or, for an
# upgradable card, a green "upgrade" button (costs gold). Anchoring on the yellow button keeps the
# green progress bar under an upgradable card from being taken for the upgrade button.
function FindMenu($bmp, $cx, $cy) {
  $bx = [Math]::Max(5, $cx - 35)
  $runTop = -1; $yTop = -1
  for ($y = $cy + 60; $y -le [Math]::Min(990, $cy + 280); $y++) {
    if (IsYellow ($bmp.GetPixel($bx, $y))) { if ($runTop -lt 0) { $runTop = $y } }
    elseif ($runTop -ge 0) {
      if (($y - 1 - $runTop) -ge 25) { $yTop = $runTop; break }
      $runTop = -1
    }
  }
  if ($yTop -lt 0) { return @{ kind = 'none' } }
  $blue = 0; $green = 0; $bMin = 9999; $bMax = -1
  for ($y = $yTop - 70; $y -le $yTop - 8; $y += 2) { for ($x = [Math]::Max(0, $cx - 60); $x -le [Math]::Min(558, $cx + 60); $x += 3) {
    $c = $bmp.GetPixel($x, $y)
    if (IsBlue $c) { $blue++; $bMin = [Math]::Min($bMin, $y); $bMax = [Math]::Max($bMax, $y) }
    elseif (IsGreen $c) { $green++ } } }
  if ($blue -ge 15 -and $blue -gt $green) { return @{ kind = 'info'; y = [int](($bMin + $bMax) / 2) } }
  if ($green -ge 15 -and $green -gt $blue) { return @{ kind = 'upgrade' } }
  return @{ kind = 'none' }
}

# x of the highlighted page dot (224,171,0). Page counts differ by card type (3, 5, champions more),
# so paging stops when this stops moving. Dots sit at y=783 (normal/hero) or about y=821 (champion).
# x stops at 390 to stay clear of the yellow "use" button on the right.
function ActiveDotX($bmp) {
  $sum = 0; $n = 0
  for ($y = 760; $y -le 845; $y += 2) { for ($x = 150; $x -le 390; $x += 2) {
    $c = $bmp.GetPixel($x, $y)
    if ($c.R -gt 200 -and $c.G -ge 150 -and $c.G -le 190 -and $c.B -lt 30) { $sum += $x; $n++ } } }
  if ($n -lt 3) { return -1 }
  return [int]($sum / $n)
}
# Stats lists and descriptions sit on a light grey panel; videos and art do not
function LooksLikeList($bmp) {
  $light = 0; $all = 0
  for ($y = 600; $y -le 760; $y += 8) { for ($x = 60; $x -le 500; $x += 8) {
    $c = $bmp.GetPixel($x, $y); $all++
    $mx = [Math]::Max($c.R, [Math]::Max($c.G, $c.B)); $mn = [Math]::Min($c.R, [Math]::Min($c.G, $c.B))
    if ($mn -gt 170 -and ($mx - $mn) -lt 35) { $light++ } } }
  return ($light / $all) -gt 0.4
}

if ($ProbeImage) {
  $img = [System.Drawing.Bitmap]::FromFile($ProbeImage)
  foreach ($pair in $Cards.Split(';')) {
    $px, $py = $pair.Split(":") | ForEach-Object { [int]$_ }
    $m = FindMenu $img $px $py
    "($px,$py): $($m.kind) $($m.y)"
  }
  $img.Dispose()
  return
}

# The menu stays open after the dialog closes; tapping the selected card again closes it, so that the
# next card never starts with a stray menu on screen
function CloseMenu($cx, $cy, $tag) {
  Click $cx $cy
  Start-Sleep -Milliseconds 700
  $b = Grab
  if ((FindMenu $b $cx $cy).kind -ne 'none') { Fail "menu of card $tag did not close" $b }
  $b.Dispose()
}

$idx = $StartIndex
foreach ($pair in $Cards.Split(';')) {
  $cx, $cy = $pair.Split(":") | ForEach-Object { [int]$_ }
  $tag = '{0:D3}' -f $idx
  if ($cy -gt 700) { throw "card y=$cy too low: the menu would overlap the bottom bar; scroll first" }

  $b = Grab
  if (DialogKind $b) { Fail "dialog unexpectedly open before selecting card $tag" $b }
  $b.Dispose()

  # A menu hangs about 230px below its card, so only the card above in the same column can cover this one
  $b = Grab
  foreach ($dy in @(235, 117)) {
    if ((FindMenu $b $cx ($cy - $dy)).kind -ne 'none') { Fail "a menu from the card above covers card $tag at ($cx,$cy)" $b }
  }
  # Tapping a selected card closes its menu, so tap only when this card's menu is not already open
  $menu = FindMenu $b $cx $cy
  if ($menu.kind -eq 'none') {
    $b.Dispose()
    Click $cx $cy
    Start-Sleep -Milliseconds 800
    $b = Grab
    $menu = FindMenu $b $cx $cy
  }
  if ($menu.kind -eq 'upgrade') {
    # Never press the upgrade button
    Save $b "${tag}_skipped_upgrade.png"
    $b.Dispose()
    CloseMenu $cx $cy $tag
    "skipped $tag ($cx,$cy): upgrade button instead of info"
    $idx++
    continue
  }
  if ($menu.kind -ne 'info') { Fail "menu (yellow use button with the blue info button above it) not found under card $tag at ($cx,$cy)" $b }
  $b.Dispose()

  Click $cx $menu.y
  Start-Sleep -Milliseconds 1300
  $b = Grab
  $kind = DialogKind $b
  if (-not $kind) { Fail "info dialog did not open for card $tag" $b }
  Save $b "${tag}_opened.png"
  $tabs = Tabs $b
  $b.Dispose()

  foreach ($tab in $tabs) {
    $t = $tab.t
    if ($tab.x -gt 0) {
      RequireDialog | Out-Null
      Click $tab.x 945
      Start-Sleep -Milliseconds 900
    }
    $b = Grab; $prev = ActiveDotX $b; $b.Dispose()
    for ($i = 0; $i -lt 8; $i++) {
      RequireDialog | Out-Null
      Drag 120 650 440 650; Start-Sleep -Milliseconds 450
      $b = Grab; $now = ActiveDotX $b; $b.Dispose()
      if ([Math]::Abs($now - $prev) -lt 3) { break }
      $prev = $now
    }
    for ($p = 1; $p -le 8; $p++) {
      Start-Sleep -Milliseconds 350
      $b = Grab
      if (-not (DialogKind $b)) { Fail "dialog closed while paging card $tag tab $t" $b }
      Save $b "${tag}_${t}_p$p.png"
      $dot = ActiveDotX $b
      if ($dot -lt 0) { "warning: page dots not found on card $tag tab $t page $p" }
      if (LooksLikeList $b) {
        $before = Signature $b 60 500 520 800
        Drag 280 740 280 590 20 20 200
        Start-Sleep -Milliseconds 500
        $b2 = Grab
        if (-not (DialogKind $b2)) { Fail "dialog closed after scrolling card $tag tab $t" $b2 }
        if ((SigDiff $before (Signature $b2 60 500 520 800)) -gt 4) { Save $b2 "${tag}_${t}_p${p}_scrolled.png" }
        $b2.Dispose()
      }
      $b.Dispose()
      RequireDialog | Out-Null
      Drag 440 650 120 650; Start-Sleep -Milliseconds 450
      $b = Grab; $next = ActiveDotX $b; $b.Dispose()
      if ([Math]::Abs($next - $dot) -lt 3) { break }
    }
  }

  $kind = RequireDialog
  $x, $y = CloseXY $kind
  Click $x $y
  Start-Sleep -Milliseconds 900
  $b = Grab
  if (DialogKind $b) { Fail "dialog did not close for card $tag" $b }
  $b.Dispose()
  CloseMenu $cx $cy $tag
  "done $tag ($cx,$cy) tabs=$(($tabs | ForEach-Object { $_.t }) -join '')"
  $idx++
}
$b = Grab; Save $b ("grid_after_{0:D3}.png" -f ($idx - 1)); $b.Dispose()
"finished; next index $idx"
