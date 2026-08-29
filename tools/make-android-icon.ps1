# Draw the interim 365+ mark as Android launcher icons.
#
# The same tile as tools\make-icon.ps1 draws for Windows, at the densities
# Android wants, so the phone and the laptop are recognisably one app. Interim
# on the same terms: not branding, just a deliberate-looking mark instead of the
# default Android robot. When real artwork lands, replace the mipmaps and delete
# both scripts.
#
#   powershell -NoProfile -File tools\make-android-icon.ps1
#
# Android launcher icons are square PNGs. The adaptive-icon foreground needs a
# generous safe margin because launchers mask it to a circle, a squircle or a
# rounded square depending on the phone -- so the mark is drawn smaller there and
# the tile is left to the background layer.

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$repo = Split-Path -Parent $PSScriptRoot
$res  = Join-Path $repo "android\src\main\res"

# The app's own palette, so the icon and the window agree.
$bg   = [System.Drawing.Color]::FromArgb(255, 10, 14, 19)   # Plus.Background
$teal = [System.Drawing.Color]::FromArgb(255, 0, 196, 140)  # Plus.Money

# mipmap density -> launcher icon size in px.
$densities = @{
    "mdpi"    = 48
    "hdpi"    = 72
    "xhdpi"   = 96
    "xxhdpi"  = 144
    "xxxhdpi" = 192
}

function New-Tile {
    param([int]$Size, [double]$Inset = 0.0, [bool]$RoundedTile = $true, [bool]$Circular = $false)

    $bmp = New-Object System.Drawing.Bitmap -ArgumentList @([int]$Size, [int]$Size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $g.Clear([System.Drawing.Color]::Transparent)

    $pad = [int]($Size * $Inset)
    $s = $Size - ($pad * 2)

    if ($RoundedTile) {
        $path = New-Object System.Drawing.Drawing2D.GraphicsPath
        if ($Circular) {
            # A launcher that asks for the round icon masks to a circle. A square
            # tile inside that mask has its corners cut off and reads as a
            # mistake, so the round variant is actually round.
            $path.AddEllipse($pad, $pad, $s, $s)
        } else {
            $r = [Math]::Max(2, [int]($s * 0.22))
            $d = $r * 2
            $path.AddArc($pad, $pad, $d, $d, 180, 90)
            $path.AddArc($pad + $s - $d, $pad, $d, $d, 270, 90)
            $path.AddArc($pad + $s - $d, $pad + $s - $d, $d, $d, 0, 90)
            $path.AddArc($pad, $pad + $s - $d, $d, $d, 90, 90)
        }
        $path.CloseFigure()
        $fill = New-Object System.Drawing.SolidBrush($bg)
        $g.FillPath($fill, $path)
        $fill.Dispose()

        $pen = New-Object System.Drawing.Pen -ArgumentList @($teal, [single]([Math]::Max(1, $s / 32)))
        $pen.Alignment = [System.Drawing.Drawing2D.PenAlignment]::Inset
        $g.DrawPath($pen, $path)
        $pen.Dispose()
        $path.Dispose()
    }

    $brush = New-Object System.Drawing.SolidBrush($teal)
    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Alignment = [System.Drawing.StringAlignment]::Center
    $fmt.LineAlignment = [System.Drawing.StringAlignment]::Center

    $f = New-Object System.Drawing.Font -ArgumentList @("Segoe UI", [single]($s * 0.30), [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $rc = New-Object System.Drawing.RectangleF -ArgumentList @([single]$pad, [single]($pad - ($s * 0.05)), [single]$s, [single]$s)
    $g.DrawString("365", $f, $brush, $rc, $fmt)
    $f.Dispose()

    $fp = New-Object System.Drawing.Font -ArgumentList @("Segoe UI", [single]($s * 0.28), [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $rp = New-Object System.Drawing.RectangleF -ArgumentList @([single]$pad, [single]($pad + ($s * 0.25)), [single]$s, [single]$s)
    $g.DrawString("+", $fp, $brush, $rp, $fmt)
    $fp.Dispose()

    $brush.Dispose(); $fmt.Dispose(); $g.Dispose()
    return $bmp
}

foreach ($d in $densities.Keys) {
    $dir = Join-Path $res "mipmap-$d"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $size = $densities[$d]

    # The legacy square tile, and the circular one round-icon launchers ask for.
    $square = New-Tile -Size $size -Inset 0.0 -RoundedTile $true -Circular $false
    $square.Save((Join-Path $dir "ic_launcher.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $square.Dispose()

    $round = New-Tile -Size $size -Inset 0.0 -RoundedTile $true -Circular $true
    $round.Save((Join-Path $dir "ic_launcher_round.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $round.Dispose()

    # The adaptive foreground. Drawn at 108dp-equivalent with the mark well
    # inside, because the launcher masks the outer third away.
    $fgSize = [int]($size * 108 / 48)
    $fg = New-Tile -Size $fgSize -Inset 0.29 -RoundedTile $false
    $fg.Save((Join-Path $dir "ic_launcher_foreground.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $fg.Dispose()

    Write-Host "mipmap-$d : ${size}px tile, ${fgSize}px adaptive foreground"
}

# Read one back, so a broken write cannot pass as a written icon.
$check = Join-Path $res "mipmap-xxxhdpi\ic_launcher.png"
$img = [System.Drawing.Bitmap]::FromFile($check)
Write-Host "verified $check is $($img.Width)x$($img.Height)"
$img.Dispose()
