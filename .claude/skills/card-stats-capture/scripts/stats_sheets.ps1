param(
  [Parameter(Mandatory)][string]$InDir,
  [Parameter(Mandatory)][string]$OutDir,
  [int]$PerSheet = 9
)
# Picks the pages that hold stats and tiles their name/level strip and stats panel, 3 per row,
# so that many cards can be read from one image.
#   normal/evolution tab (3 pages): page 2; champion (normal tab with 5 pages): pages 3 and 4;
#   hero tab: pages 3 (ability) and 4 (body); plus any *_scrolled image of those pages.
Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force $OutDir | Out-Null

$all = Get-ChildItem $InDir -Filter '*.png' | Where-Object { $_.Name -match '^\d{3}_[neh]_p\d' }
$pick = foreach ($f in $all) {
  if ($f.Name -notmatch '^(\d{3})_([neh])_p(\d)(_scrolled)?\.png$') { continue }
  $id = $Matches[1]; $t = $Matches[2]; $p = [int]$Matches[3]
  $pages = ($all | Where-Object { $_.Name -match "^${id}_${t}_p\d\.png$" }).Count
  $want = if ($t -eq 'h' -or ($t -eq 'n' -and $pages -ge 5)) { @(3, 4) } else { @(2) }
  if ($want -contains $p) { $f }
}
$pick = $pick | Sort-Object Name

function IsRed($c) { $c.R -gt 200 -and $c.G -lt 130 -and $c.B -lt 130 }
$W = 480; $label = 18; $titleH = 75; $panelH = 320; $H = $label + $titleH + $panelH
$font = New-Object System.Drawing.Font 'Arial', 11, ([System.Drawing.FontStyle]::Bold)
$n = 0; $sheet = 0
for ($i = 0; $i -lt $pick.Count; $i += $PerSheet) {
  $batch = $pick[$i..([Math]::Min($pick.Count, $i + $PerSheet) - 1)]
  $rows = [Math]::Ceiling($batch.Count / 3)
  $bmp = New-Object System.Drawing.Bitmap (3 * $W), ($rows * $H)
  $g = [System.Drawing.Graphics]::FromImage($bmp); $g.Clear([System.Drawing.Color]::White)
  for ($k = 0; $k -lt $batch.Count; $k++) {
    $src = [System.Drawing.Bitmap]::FromFile($batch[$k].FullName)
    # Tall dialogs (hero tab, champions) have the close button and the name near the top
    $tall = (IsRed $src.GetPixel(487, 47)) -and -not (IsRed $src.GetPixel(487, 287))
    $titleY = if ($tall) { 45 } else { 290 }
    $x = ($k % 3) * $W; $y = [Math]::Floor($k / 3) * $H
    $g.DrawString($batch[$k].BaseName, $font, [System.Drawing.Brushes]::Black, $x + 4, $y + 1)
    $g.DrawImage($src, (New-Object System.Drawing.Rectangle $x, ($y + $label), $W, $titleH), (New-Object System.Drawing.Rectangle 40, $titleY, $W, $titleH), [System.Drawing.GraphicsUnit]::Pixel)
    $g.DrawImage($src, (New-Object System.Drawing.Rectangle $x, ($y + $label + $titleH), $W, $panelH), (New-Object System.Drawing.Rectangle 40, 495, $W, $panelH), [System.Drawing.GraphicsUnit]::Pixel)
    $src.Dispose()
  }
  $sheet++
  $out = Join-Path $OutDir ("sheet_{0:D2}.png" -f $sheet)
  $bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png); $g.Dispose(); $bmp.Dispose()
  "$out : " + (($batch | ForEach-Object { $_.BaseName }) -join ' ')
}
"pages: $($pick.Count), sheets: $sheet"
