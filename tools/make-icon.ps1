# Draw the interim 365+ mark and assemble it into a multi-size .ico.
#
# Interim on purpose: a dark tile with a teal "365+" in the app's own accent, so
# the taskbar entry looks deliberate rather than like a default. It is not
# branding — when real artwork arrives, drop the .ico in and delete this script.
#
#   powershell -NoProfile -File tools\make-icon.ps1
#
# Windows caches taskbar icons aggressively, so a changed icon may not show until
# the app is reinstalled and relaunched.

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$repo = Split-Path -Parent $PSScriptRoot
$out  = Join-Path $repo "desktop\icons\plus365.ico"
New-Item -ItemType Directory -Force -Path (Split-Path $out) | Out-Null

# The app's own palette, so the icon and the window agree.
$bg   = [System.Drawing.Color]::FromArgb(255, 10, 14, 19)   # Plus.Background
$teal = [System.Drawing.Color]::FromArgb(255, 0, 196, 140)  # Plus.Money

# 256 is deliberately absent. It can only be stored sensibly as PNG-in-ICO, and
# that is the format System.Drawing cannot read back — which would leave the file
# unverifiable here. Windows scales 128 up perfectly well for the rare places it
# wants 256.
$sizes = @(16, 24, 32, 48, 64, 128)

function New-Tile([int]$s) {
    $bmp = New-Object System.Drawing.Bitmap -ArgumentList @([int]$s, [int]$s, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $g.Clear([System.Drawing.Color]::Transparent)

    # Rounded tile. The radius scales so small sizes do not turn into circles.
    $r = [Math]::Max(2, [int]($s * 0.22))
    $d = $r * 2
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddArc(0, 0, $d, $d, 180, 90)
    $path.AddArc($s - $d, 0, $d, $d, 270, 90)
    $path.AddArc($s - $d, $s - $d, $d, $d, 0, 90)
    $path.AddArc(0, $s - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    $fill = New-Object System.Drawing.SolidBrush($bg)
    $g.FillPath($fill, $path)
    $fill.Dispose()

    # A thin teal edge so the tile reads on a dark taskbar as well as a light one.
    if ($s -ge 32) {
        $pen = New-Object System.Drawing.Pen -ArgumentList @($teal, [single]([Math]::Max(1, $s / 32)))
        $pen.Alignment = [System.Drawing.Drawing2D.PenAlignment]::Inset
        $g.DrawPath($pen, $path)
        $pen.Dispose()
    }

    $brush = New-Object System.Drawing.SolidBrush($teal)
    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Alignment = [System.Drawing.StringAlignment]::Center
    $fmt.LineAlignment = [System.Drawing.StringAlignment]::Center

    if ($s -le 24) {
        # Too small for four glyphs. A bare "+" stays legible where "365+" would
        # collapse into a smudge, and the tile still reads as the same app.
        $f = New-Object System.Drawing.Font -ArgumentList @("Segoe UI", [single]($s * 0.62), [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
        $rc = New-Object System.Drawing.RectangleF -ArgumentList @([single]0, [single]0, [single]$s, [single]$s)
        $g.DrawString("+", $f, $brush, $rc, $fmt)
        $f.Dispose()
    } else {
        $f = New-Object System.Drawing.Font -ArgumentList @("Segoe UI", [single]($s * 0.30), [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
        $rc = New-Object System.Drawing.RectangleF -ArgumentList @([single]0, [single](-($s * 0.05)), [single]$s, [single]$s)
        $g.DrawString("365", $f, $brush, $rc, $fmt)
        $f.Dispose()
        $fp = New-Object System.Drawing.Font -ArgumentList @("Segoe UI", [single]($s * 0.28), [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
        $rp = New-Object System.Drawing.RectangleF -ArgumentList @([single]0, [single]($s * 0.25), [single]$s, [single]$s)
        $g.DrawString("+", $fp, $brush, $rp, $fmt)
        $fp.Dispose()
    }
    $brush.Dispose(); $fmt.Dispose(); $path.Dispose(); $g.Dispose()
    return $bmp
}

# Each entry is a 32-bit DIB, not a PNG.
#
# PNG-in-ICO is legal and Windows renders it, but System.Drawing cannot read it
# back — which would mean shipping an icon that could not be checked here. A
# DIB can be verified, and verification is worth more than the few kilobytes.
function ConvertTo-IconDib([System.Drawing.Bitmap]$bmp) {
    $w = $bmp.Width; $h = $bmp.Height
    $rect = New-Object System.Drawing.Rectangle -ArgumentList @(0, 0, $w, $h)
    $data = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $stride = $data.Stride
    $pixels = New-Object byte[] ($stride * $h)
    [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $pixels, 0, $pixels.Length)
    $bmp.UnlockBits($data)

    $ms = New-Object System.IO.MemoryStream
    $bw = New-Object System.IO.BinaryWriter($ms)

    # BITMAPINFOHEADER. Height is doubled because the mask counts as image rows.
    $bw.Write([UInt32]40)
    $bw.Write([Int32]$w)
    $bw.Write([Int32]($h * 2))
    $bw.Write([UInt16]1)
    $bw.Write([UInt16]32)
    $bw.Write([UInt32]0)              # BI_RGB
    $bw.Write([UInt32]($w * $h * 4))
    $bw.Write([Int32]0); $bw.Write([Int32]0); $bw.Write([UInt32]0); $bw.Write([UInt32]0)

    # Colour data, bottom-up. GDI gives it top-down, so walk the rows backwards.
    for ($y = $h - 1; $y -ge 0; $y--) {
        $bw.Write($pixels, $y * $stride, $w * 4)
    }

    # The AND mask. Zero everywhere: the alpha channel already carries the
    # transparency, and a stale mask would punch holes in a 32-bit icon.
    $maskRow = [Math]::Floor(($w + 31) / 32) * 4
    $blank = New-Object byte[] $maskRow
    for ($y = 0; $y -lt $h; $y++) { $bw.Write($blank) }

    $bw.Flush()
    $bytes = $ms.ToArray()
    $bw.Close(); $ms.Dispose()
    # The comma matters: PowerShell unrolls a returned array into the pipeline,
    # so without it the caller gets the first byte instead of the picture.
    return ,$bytes
}

$entries = @()
foreach ($s in $sizes) {
    $bmp = New-Tile $s
    $entries += ,@($s, (ConvertTo-IconDib $bmp))
    $bmp.Dispose()
}

$fs = [System.IO.File]::Create($out)
$bw = New-Object System.IO.BinaryWriter($fs)
$bw.Write([UInt16]0)                 # reserved
$bw.Write([UInt16]1)                 # type: icon
$bw.Write([UInt16]$entries.Count)

# Directory entries come first, so every offset must clear the whole table.
$offset = 6 + (16 * $entries.Count)
foreach ($e in $entries) {
    $s = $e[0]; $bytes = $e[1]
    $bw.Write([Byte]$s)
    $bw.Write([Byte]$s)
    $bw.Write([Byte]0)               # palette count
    $bw.Write([Byte]0)               # reserved
    $bw.Write([UInt16]1)             # colour planes
    $bw.Write([UInt16]32)            # bits per pixel
    $bw.Write([UInt32]$bytes.Length)
    $bw.Write([UInt32]$offset)
    $offset += $bytes.Length
}
foreach ($e in $entries) { $bw.Write($e[1]) }
$bw.Flush(); $bw.Close(); $fs.Close()

# Read it back at every size. An icon that cannot be loaded is an icon that will
# quietly fall back to the default, which is the thing this replaces.
foreach ($s in $sizes) {
    $i = New-Object System.Drawing.Icon -ArgumentList @($out, [int]$s, [int]$s)
    $b = $i.ToBitmap()
    if ($b.Width -ne $s) { throw "icon size $s read back as $($b.Width)" }
    $b.Dispose(); $i.Dispose()
}

Write-Host ("Wrote {0} ({1:N0} bytes, {2} sizes verified: {3})" -f $out, (Get-Item $out).Length, $entries.Count, ($sizes -join ", "))
