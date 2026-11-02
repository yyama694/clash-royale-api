param([int]$X, [int]$Y, [ValidateSet('post', 'send')][string]$Method = 'post', [int]$ToX = -1, [int]$ToY = -1, [int]$Steps = 15, [int]$StepMs = 15)
# X,Y are coordinates in the captured image (the main window's client area, as capture.ps1 saves it).
# The game draws into a child window (class CROSVM_1). When the Google Play Games sidebar is shown, that child
# starts at client (64,36) and messages posted to the main window never reach the game, so 'post' sends them
# to the child with the coordinates shifted into it (2026-11-02).

Add-Type @"
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class C {
  public delegate bool EnumProc(IntPtr h, IntPtr l);
  [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);
  [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint m, IntPtr w, IntPtr l);
  [DllImport("user32.dll")] public static extern bool GetCursorPos(out POINT p);
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern void mouse_event(uint f, int dx, int dy, uint d, IntPtr e);
  [DllImport("user32.dll")] public static extern bool EnumChildWindows(IntPtr p, EnumProc f, IntPtr l);
  [DllImport("user32.dll")] public static extern int GetClassName(IntPtr h, StringBuilder s, int n);
  public static IntPtr Find(IntPtr p, string cls) {
    IntPtr found = IntPtr.Zero;
    EnumChildWindows(p, (h, x) => { var s = new StringBuilder(256); GetClassName(h, s, 256); if (s.ToString() == cls) { found = h; return false; } return true; }, IntPtr.Zero);
    return found;
  }
}
"@
[C]::SetProcessDPIAware() | Out-Null
$proc = Get-Process crosvm | Where-Object { $_.MainWindowTitle -match 'Clash Royale' } | Select-Object -First 1
$main = $proc.MainWindowHandle
$h = $main
$dx = 0; $dy = 0
$child = [C]::Find($main, 'CROSVM_1')
if ($child -ne [IntPtr]::Zero) {
  $a = New-Object C+POINT; [C]::ClientToScreen($main, [ref]$a) | Out-Null
  $b = New-Object C+POINT; [C]::ClientToScreen($child, [ref]$b) | Out-Null
  $h = $child; $dx = $b.X - $a.X; $dy = $b.Y - $a.Y
}

function LParam([int]$x, [int]$y) { [IntPtr]((($y - $dy) -shl 16) -bor (($x - $dx) -band 0xFFFF)) }

if ($Method -eq 'post' -and $ToX -ge 0) {
  [C]::PostMessage($h, 0x0200, [IntPtr]0, (LParam $X $Y)) | Out-Null
  [C]::PostMessage($h, 0x0201, [IntPtr]1, (LParam $X $Y)) | Out-Null
  for ($i = 1; $i -le $Steps; $i++) {
    Start-Sleep -Milliseconds $StepMs
    $cx = [int]($X + ($ToX - $X) * $i / $Steps); $cy = [int]($Y + ($ToY - $Y) * $i / $Steps)
    [C]::PostMessage($h, 0x0200, [IntPtr]1, (LParam $cx $cy)) | Out-Null
  }
  Start-Sleep -Milliseconds 80
  [C]::PostMessage($h, 0x0202, [IntPtr]0, (LParam $ToX $ToY)) | Out-Null
  "posted drag ($X,$Y) -> ($ToX,$ToY) offset=($dx,$dy)"
} elseif ($Method -eq 'post') {
  $lp = LParam $X $Y
  [C]::PostMessage($h, 0x0200, [IntPtr]0, $lp) | Out-Null
  [C]::PostMessage($h, 0x0201, [IntPtr]1, $lp) | Out-Null
  Start-Sleep -Milliseconds 80
  [C]::PostMessage($h, 0x0202, [IntPtr]0, $lp) | Out-Null
  "posted click at client ($X,$Y) offset=($dx,$dy)"
} else {
  # Moves the real cursor, so put the cursor and the foreground window back afterwards
  $prevFg = [C]::GetForegroundWindow()
  $prev = New-Object C+POINT
  [C]::GetCursorPos([ref]$prev) | Out-Null
  $p = New-Object C+POINT; $p.X = $X; $p.Y = $Y
  [C]::ClientToScreen($main, [ref]$p) | Out-Null
  [C]::SetForegroundWindow($main) | Out-Null
  Start-Sleep -Milliseconds 100
  [C]::SetCursorPos($p.X, $p.Y) | Out-Null
  Start-Sleep -Milliseconds 50
  [C]::mouse_event(0x0002, 0, 0, 0, [IntPtr]::Zero)
  Start-Sleep -Milliseconds 80
  [C]::mouse_event(0x0004, 0, 0, 0, [IntPtr]::Zero)
  Start-Sleep -Milliseconds 100
  [C]::SetCursorPos($prev.X, $prev.Y) | Out-Null
  [C]::SetForegroundWindow($prevFg) | Out-Null
  "sent click at screen ($($p.X),$($p.Y))"
}
