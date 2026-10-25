param([string]$Spec)
# Spec: "file.png:x,y x,y|file2.png:x,y" (the last colon separates the file from the points, so absolute paths work)
Add-Type -AssemblyName System.Drawing
$base = Split-Path -Parent $MyInvocation.MyCommand.Path
foreach ($part in $Spec.Split('|')) {
  $i = $part.LastIndexOf(':')
  $f = $part.Substring(0, $i); $pts = $part.Substring($i + 1)
  $path = if ([IO.Path]::IsPathRooted($f)) { $f } else { Join-Path $base $f }
  $bmp = [System.Drawing.Bitmap]::FromFile($path)
  $out = foreach ($pt in $pts.Trim().Split(' ')) {
    $x, $y = $pt.Split(',') | ForEach-Object { [int]$_ }
    $c = $bmp.GetPixel($x, $y)
    "($x,$y)=$($c.R),$($c.G),$($c.B)"
  }
  "$f  " + ($out -join '  ')
  $bmp.Dispose()
}
