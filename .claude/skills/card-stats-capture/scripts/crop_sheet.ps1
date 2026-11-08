param([string[]]$Files, [string]$OutFile, [int]$NameY = 90, [int]$AbY = 685, [int]$AbH = 110)
Add-Type -AssemblyName System.Drawing
$w = 900; $rowH = 70 + $AbH
$out = New-Object System.Drawing.Bitmap($w, ($rowH * $Files.Count))
$g = [System.Drawing.Graphics]::FromImage($out)
$g.Clear([System.Drawing.Color]::Black)
$font = New-Object System.Drawing.Font("Arial", 14)
$i = 0
foreach ($f in $Files) {
    $b = [System.Drawing.Bitmap]::FromFile($f)
    $y0 = $i * $rowH
    $g.DrawString([System.IO.Path]::GetFileNameWithoutExtension($f), $font, [System.Drawing.Brushes]::White, 0, $y0)
    $g.DrawImage($b, (New-Object System.Drawing.Rectangle(100, $y0, 480, 70)), (New-Object System.Drawing.Rectangle(100, $NameY, 480, 70)), [System.Drawing.GraphicsUnit]::Pixel)
    $g.DrawImage($b, (New-Object System.Drawing.Rectangle(100, ($y0 + 70), 420, $AbH)), (New-Object System.Drawing.Rectangle(130, $AbY, 420, $AbH)), [System.Drawing.GraphicsUnit]::Pixel)
    $b.Dispose()
    $i++
}
$g.Dispose()
$out.Save($OutFile, [System.Drawing.Imaging.ImageFormat]::Png)
$out.Dispose()
Write-Output "$($Files.Count) -> $OutFile"
