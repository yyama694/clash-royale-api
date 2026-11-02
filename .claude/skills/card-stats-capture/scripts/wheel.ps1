param([int]$X = 280, [int]$Y = 600, [int]$Notches = -3)
# Sends mouse wheel notches to the game window (negative = scroll down). Unlike a drag, nothing is
# pressed, so a card cannot be picked up and dropped into the deck by accident.
# X,Y are coordinates in the captured image. Messages go to the game's child window (CROSVM_1) when it exists,
# because with the Google Play Games sidebar shown the main window does not pass them on (2026-11-02).
Add-Type @"
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class Wh {
  public delegate bool EnumProc(IntPtr h, IntPtr l);
  [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);
  [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint m, IntPtr w, IntPtr l);
  [DllImport("user32.dll")] public static extern bool EnumChildWindows(IntPtr p, EnumProc f, IntPtr l);
  [DllImport("user32.dll")] public static extern int GetClassName(IntPtr h, StringBuilder s, int n);
  public static IntPtr Find(IntPtr p, string cls) {
    IntPtr found = IntPtr.Zero;
    EnumChildWindows(p, (h, x) => { var s = new StringBuilder(256); GetClassName(h, s, 256); if (s.ToString() == cls) { found = h; return false; } return true; }, IntPtr.Zero);
    return found;
  }
}
"@
[Wh]::SetProcessDPIAware() | Out-Null
$main = (Get-Process crosvm | Where-Object { $_.MainWindowTitle -match 'Clash Royale' } | Select-Object -First 1).MainWindowHandle
$h = $main
$child = [Wh]::Find($main, 'CROSVM_1')
if ($child -ne [IntPtr]::Zero) { $h = $child }
$p = New-Object Wh+POINT; $p.X = $X; $p.Y = $Y
[Wh]::ClientToScreen($main, [ref]$p) | Out-Null
# WM_MOUSEWHEEL takes screen coordinates in lParam
$lp = [IntPtr]((($p.Y -band 0xFFFF) -shl 16) -bor ($p.X -band 0xFFFF))
$step = if ($Notches -lt 0) { -120 } else { 120 }
for ($i = 0; $i -lt [Math]::Abs($Notches); $i++) {
  $wp = [IntPtr]([int64](($step -band 0xFFFF) -shl 16))
  [Wh]::PostMessage($h, 0x020A, $wp, $lp) | Out-Null
  Start-Sleep -Milliseconds 120
}
"wheel $Notches at client ($X,$Y)"
