param(
  [Parameter(Mandatory)][string[]]$Paths,
  [int]$Top = 0, [int]$Bottom = 0,   # optional crop rows (0 = whole image)
  [int]$Scale = 2                    # upscaling helps the Japanese OCR with the small digits
)
# Prints the text Windows OCR (Japanese) finds in each image, one line per OCR line with its position.
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
  $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await($op, $type) {
  $t = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($op)); $t.Wait(-1) | Out-Null; $t.Result
}
$null = [Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime]
$null = [Windows.Media.Ocr.OcrEngine, Windows.Foundation, ContentType = WindowsRuntime]
$null = [Windows.Graphics.Imaging.BitmapDecoder, Windows.Graphics, ContentType = WindowsRuntime]
$null = [Windows.Globalization.Language, Windows.Globalization, ContentType = WindowsRuntime]
$engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromLanguage([Windows.Globalization.Language]::new('ja'))
$tmp = Join-Path $env:TEMP ("ocr_{0}.png" -f [guid]::NewGuid())

foreach ($path in $Paths) {
  $src = [System.Drawing.Bitmap]::FromFile((Resolve-Path $path).Path)
  $y0 = $Top; $y1 = if ($Bottom -gt 0) { $Bottom } else { $src.Height }
  $dst = New-Object System.Drawing.Bitmap ($src.Width * $Scale), (($y1 - $y0) * $Scale)
  $g = [System.Drawing.Graphics]::FromImage($dst)
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.DrawImage($src, (New-Object System.Drawing.Rectangle 0, 0, $dst.Width, $dst.Height), (New-Object System.Drawing.Rectangle 0, $y0, $src.Width, ($y1 - $y0)), [System.Drawing.GraphicsUnit]::Pixel)
  $g.Dispose(); $src.Dispose()
  $dst.Save($tmp, [System.Drawing.Imaging.ImageFormat]::Png); $dst.Dispose()

  $file = Await ([Windows.Storage.StorageFile]::GetFileFromPathAsync($tmp)) ([Windows.Storage.StorageFile])
  $stream = Await ($file.OpenAsync([Windows.Storage.FileAccessMode]::Read)) ([Windows.Storage.Streams.IRandomAccessStream])
  $decoder = Await ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($stream)) ([Windows.Graphics.Imaging.BitmapDecoder])
  $bitmap = Await ($decoder.GetSoftwareBitmapAsync()) ([Windows.Graphics.Imaging.SoftwareBitmap])
  $result = Await ($engine.RecognizeAsync($bitmap)) ([Windows.Media.Ocr.OcrResult])
  "=== $(Split-Path -Leaf $path)"
  foreach ($line in $result.Lines) {
    # Words is a WinRT vector; indexing it directly yields an array in PowerShell 5.1
    $r = ($line.Words | Select-Object -First 1).BoundingRect
    # Japanese OCR puts spaces between characters; drop them
    "{0},{1}`t{2}" -f [int]($r.X / $Scale), [int]($r.Y / $Scale + $y0), ($line.Text -replace ' ', '')
  }
  $stream.Dispose()
}
Remove-Item $tmp -ErrorAction SilentlyContinue
