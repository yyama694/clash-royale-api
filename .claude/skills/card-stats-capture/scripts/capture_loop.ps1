param([string]$OutDir, [int]$Seconds = 180, [double]$IntervalSec = 1.0, [double]$Threshold = 6.0)

Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class W {
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint flags);
}
"@
[W]::SetProcessDPIAware() | Out-Null
New-Item -ItemType Directory -Force $OutDir | Out-Null

function Capture {
  $proc = Get-Process crosvm -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -match 'Clash Royale' } | Select-Object -First 1
  if (-not $proc) { return $null }
  $h = $proc.MainWindowHandle
  $r = New-Object W+RECT
  [W]::GetClientRect($h, [ref]$r) | Out-Null
  $bmp = New-Object System.Drawing.Bitmap ($r.R - $r.L), ($r.B - $r.T)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $hdc = $g.GetHdc()
  [W]::PrintWindow($h, $hdc, 3) | Out-Null
  $g.ReleaseHdc($hdc); $g.Dispose()
  return $bmp
}

# Change detection looks only at the stats grid area, because card art elsewhere is animated
function Signature($bmp) {
  $cols = 48; $rows = 24
  $x0 = [int]($bmp.Width * 0.08); $x1 = [int]($bmp.Width * 0.92)
  $y0 = [int]($bmp.Height * 0.50); $y1 = [int]($bmp.Height * 0.76)
  $sig = New-Object double[] ($cols * $rows)
  for ($j = 0; $j -lt $rows; $j++) {
    for ($i = 0; $i -lt $cols; $i++) {
      $c = $bmp.GetPixel($x0 + [int](($x1 - $x0) * $i / $cols), $y0 + [int](($y1 - $y0) * $j / $rows))
      $sig[$j * $cols + $i] = 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B
    }
  }
  return ,$sig
}

$last = $null; $n = 0
$end = (Get-Date).AddSeconds($Seconds)
while ((Get-Date) -lt $end) {
  $bmp = Capture
  if ($bmp) {
    $sig = Signature $bmp
    $diff = 999
    if ($last) {
      $sum = 0.0
      for ($k = 0; $k -lt $sig.Length; $k++) { $sum += [Math]::Abs($sig[$k] - $last[$k]) }
      $diff = $sum / $sig.Length
    }
    if ($diff -gt $Threshold) {
      $n++
      $file = Join-Path $OutDir ("{0:D3}_{1}.png" -f $n, (Get-Date -Format 'HHmmss'))
      $bmp.Save($file, [System.Drawing.Imaging.ImageFormat]::Png)
      "saved $file diff=$([Math]::Round($diff,1))"
      $last = $sig
    }
    $bmp.Dispose()
  }
  Start-Sleep -Milliseconds ([int]($IntervalSec * 1000))
}
"done: $n images"
