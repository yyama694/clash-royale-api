param([int]$X, [int]$Y, [ValidateSet('post', 'send')][string]$Method = 'post', [int]$ToX = -1, [int]$ToY = -1, [int]$Steps = 15, [int]$StepMs = 15)

Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class C {
  [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);
  [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint m, IntPtr w, IntPtr l);
  [DllImport("user32.dll")] public static extern bool GetCursorPos(out POINT p);
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern void mouse_event(uint f, int dx, int dy, uint d, IntPtr e);
}
"@
[C]::SetProcessDPIAware() | Out-Null
$proc = Get-Process crosvm | Where-Object { $_.MainWindowTitle -match 'Clash Royale' } | Select-Object -First 1
$h = $proc.MainWindowHandle

function LParam([int]$x, [int]$y) { [IntPtr](($y -shl 16) -bor ($x -band 0xFFFF)) }

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
  "posted drag ($X,$Y) -> ($ToX,$ToY)"
} elseif ($Method -eq 'post') {
  $lp = LParam $X $Y
  [C]::PostMessage($h, 0x0200, [IntPtr]0, $lp) | Out-Null
  [C]::PostMessage($h, 0x0201, [IntPtr]1, $lp) | Out-Null
  Start-Sleep -Milliseconds 80
  [C]::PostMessage($h, 0x0202, [IntPtr]0, $lp) | Out-Null
  "posted click at client ($X,$Y)"
} else {
  # Moves the real cursor, so put the cursor and the foreground window back afterwards
  $prevFg = [C]::GetForegroundWindow()
  $prev = New-Object C+POINT
  [C]::GetCursorPos([ref]$prev) | Out-Null
  $p = New-Object C+POINT; $p.X = $X; $p.Y = $Y
  [C]::ClientToScreen($h, [ref]$p) | Out-Null
  [C]::SetForegroundWindow($h) | Out-Null
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
