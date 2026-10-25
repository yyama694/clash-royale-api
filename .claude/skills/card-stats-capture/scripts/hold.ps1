param([int]$X, [int]$Y, [int]$HoldMs = 1500, [string]$OutFile)
# Presses and holds at (X,Y) in the game window and captures the window while it is held.
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class Hd {
  [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint m, IntPtr w, IntPtr l);
}
"@
$proc = Get-Process crosvm | Where-Object { $_.MainWindowTitle -match 'Clash Royale' } | Select-Object -First 1
$h = $proc.MainWindowHandle
$lp = [IntPtr](($Y -shl 16) -bor ($X -band 0xFFFF))
[Hd]::PostMessage($h, 0x0200, [IntPtr]0, $lp) | Out-Null
[Hd]::PostMessage($h, 0x0201, [IntPtr]1, $lp) | Out-Null
Start-Sleep -Milliseconds $HoldMs
if ($OutFile) { powershell -NoProfile -ExecutionPolicy Bypass -File "$dir\capture.ps1" -ProcessId $proc.Id -OutFile $OutFile | Out-Null }
[Hd]::PostMessage($h, 0x0202, [IntPtr]0, $lp) | Out-Null
"held ($X,$Y) for $HoldMs ms"
