param([int]$ProcessId, [string]$OutFile)

Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class W {
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
  [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);
  [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
  [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint flags);
}
"@
[W]::SetProcessDPIAware() | Out-Null

$h = (Get-Process -Id $ProcessId).MainWindowHandle
if ([W]::IsIconic($h)) { throw "window is minimized" }
$r = New-Object W+RECT
[W]::GetClientRect($h, [ref]$r) | Out-Null
$p = New-Object W+POINT
[W]::ClientToScreen($h, [ref]$p) | Out-Null
$w = $r.R - $r.L; $ht = $r.B - $r.T

$bmp = New-Object System.Drawing.Bitmap $w, $ht
$g = [System.Drawing.Graphics]::FromImage($bmp)
# PW_CLIENTONLY(1) | PW_RENDERFULLCONTENT(2): captures even when other windows overlap it
$hdc = $g.GetHdc()
$ok = [W]::PrintWindow($h, $hdc, 3)
$g.ReleaseHdc($hdc)
if (-not $ok) { throw "PrintWindow failed" }
$bmp.Save($OutFile, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
"client origin=($($p.X),$($p.Y)) size=${w}x${ht}"
