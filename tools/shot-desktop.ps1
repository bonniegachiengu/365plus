# Photograph the 365+ window, and nothing else.
#
# Verifying an install means reading the build line under the title, which means
# a picture. A full-screen grab would also collect whatever else is on the
# screen — chats, notifications, other windows — none of which is anyone's
# business here. PrintWindow asks Windows for that one window's pixels, so the
# rest of the desktop is never in the frame at all.
#
#   pwsh -File tools\shot-desktop.ps1 -Out shot.png

param(
    [string]$Out = "$env:TEMP\plus365-window.png",
    # The installed app runs as Plus365. A `gradlew :desktop:run` copy runs as
    # java, so verifying a dev build needs to be able to say which one.
    [string]$Process = "Plus365"
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win {
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr dc, uint f);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
}
"@

$proc = Get-Process $Process -ErrorAction SilentlyContinue |
    Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -like "*365*" } |
    Select-Object -First 1
if (-not $proc) { throw "$Process has no 365+ window." }

$h = $proc.MainWindowHandle
$r = New-Object Win+RECT
[void][Win]::GetWindowRect($h, [ref]$r)
$w = $r.R - $r.L
$ht = $r.B - $r.T
if ($w -le 0 -or $ht -le 0) { throw "The window has no size yet." }

$bmp = New-Object System.Drawing.Bitmap $w, $ht
$g = [System.Drawing.Graphics]::FromImage($bmp)
$dc = $g.GetHdc()
# 2 = PW_RENDERFULLCONTENT, which is what makes this work for a GPU-composited
# window like Compose. Without it the capture comes back blank.
$ok = [Win]::PrintWindow($h, $dc, 2)
$g.ReleaseHdc($dc)
$g.Dispose()
if (-not $ok) { $bmp.Dispose(); throw "PrintWindow refused." }

# A window captured between opening and its first frame comes back as a flat
# rectangle. That is indistinguishable from an app that launched and rendered
# nothing, which is exactly the failure this script exists to rule out -- so an
# unpainted frame is refused rather than saved.
#
# The first version of this check asked whether more than one colour was present
# and passed a genuinely blank frame, because the title bar and one avatar circle
# were enough. Measured against real captures instead: an unpainted window
# samples 2 distinct colours and a painted one samples 60 to 96. Twelve sits in
# the gap with room on both sides.
#
# The sample starts below the title bar, which has colours of its own no matter
# what the app has drawn.
function Get-SampledColourCount {
    param([System.Drawing.Bitmap]$Image)
    $seen = New-Object 'System.Collections.Generic.HashSet[int]'
    $stepX = [Math]::Max(1, [int]($Image.Width / 60))
    $stepY = [Math]::Max(1, [int]($Image.Height / 60))
    for ($x = 8; $x -lt $Image.Width - 8; $x += $stepX) {
        for ($y = 40; $y -lt $Image.Height - 8; $y += $stepY) {
            [void]$seen.Add($Image.GetPixel($x, $y).ToArgb())
        }
    }
    return $seen.Count
}

$colours = Get-SampledColourCount $bmp
if ($colours -lt 12) {
    $bmp.Dispose()
    throw "The window has not painted yet (only $colours colours). Try again."
}

$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Host "Wrote $Out ($w x $ht)"
