param([int]$X = 280, [int]$Y = 600, [int]$Notches = -3)
# Sends mouse wheel notches to the game window (negative = scroll down). Unlike a drag, nothing is
# pressed, so a card cannot be picked up and dropped into the deck by accident.
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class Wh {
  [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);
  [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint m, IntPtr w, IntPtr l);
}
"@
[Wh]::SetProcessDPIAware() | Out-Null
$h = (Get-Process crosvm | Where-Object { $_.MainWindowTitle -match 'Clash Royale' } | Select-Object -First 1).MainWindowHandle
$p = New-Object Wh+POINT; $p.X = $X; $p.Y = $Y
[Wh]::ClientToScreen($h, [ref]$p) | Out-Null
# WM_MOUSEWHEEL takes screen coordinates in lParam
$lp = [IntPtr]((($p.Y -band 0xFFFF) -shl 16) -bor ($p.X -band 0xFFFF))
$step = if ($Notches -lt 0) { -120 } else { 120 }
for ($i = 0; $i -lt [Math]::Abs($Notches); $i++) {
  $wp = [IntPtr]([int64](($step -band 0xFFFF) -shl 16))
  [Wh]::PostMessage($h, 0x020A, $wp, $lp) | Out-Null
  Start-Sleep -Milliseconds 120
}
"wheel $Notches at client ($X,$Y)"
