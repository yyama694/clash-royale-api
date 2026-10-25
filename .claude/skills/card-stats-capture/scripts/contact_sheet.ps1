param([string]$InDir, [string]$OutFile, [int]$Cols = 8, [int]$ThumbW = 180)

Add-Type -AssemblyName System.Drawing
$files = Get-ChildItem $InDir -Filter *.png | Sort-Object Name
$first = [System.Drawing.Image]::FromFile($files[0].FullName)
$thumbH = [int]($first.Height * $ThumbW / $first.Width)
$first.Dispose()
$label = 18
$rows = [Math]::Ceiling($files.Count / $Cols)
$sheet = New-Object System.Drawing.Bitmap ($Cols * $ThumbW), ($rows * ($thumbH + $label))
$g = [System.Drawing.Graphics]::FromImage($sheet)
$g.Clear([System.Drawing.Color]::White)
$font = New-Object System.Drawing.Font 'Arial', 10, ([System.Drawing.FontStyle]::Bold)
for ($i = 0; $i -lt $files.Count; $i++) {
  $img = [System.Drawing.Image]::FromFile($files[$i].FullName)
  $x = ($i % $Cols) * $ThumbW; $y = [Math]::Floor($i / $Cols) * ($thumbH + $label)
  $g.DrawString($files[$i].BaseName, $font, [System.Drawing.Brushes]::Black, $x + 2, $y + 1)
  $g.DrawImage($img, $x, $y + $label, $ThumbW, $thumbH)
  $img.Dispose()
}
$sheet.Save($OutFile, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $sheet.Dispose()
"$($files.Count) images -> $OutFile"
