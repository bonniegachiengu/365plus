# Rebuild the Windows app and reinstall it over the existing one.
#
# Run this after any change you want on the pinned desktop app. The MSI carries a
# fixed upgrade UUID, so this replaces the installed build rather than adding a
# second copy — and the pinned taskbar and Start-menu entries survive, because
# they point at the same path.
#
#   pwsh -File tools\install-desktop.ps1
#
# Close the app first if it is running; Windows will not overwrite a running exe,
# and the failure it gives for that is not obvious.

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
Push-Location $repo

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.6.7-hotspot"
}

# Nothing can overwrite a running exe, so stop it before building rather than
# after — the build takes a minute and there is no reason to wait to find out.
# The jpackage launcher spawns a child of the same name, so killing the parent
# takes the child with it. Ignore the ones that are already gone by the time the
# loop reaches them, or the script dies on its own success.
# A single pass is not enough. The launcher and its child die at different
# speeds, and a fixed sleep long enough to cover the slow case is a sleep you
# pay on every run. Kill, wait, look again, up to ten seconds.
$deadline = 10
for ($i = 0; $i -lt $deadline; $i++) {
    $running = @(Get-Process Plus365 -ErrorAction SilentlyContinue)
    if ($running.Count -eq 0) { break }
    foreach ($p in $running) {
        Write-Host "Closing the running app (pid $($p.Id))"
        Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 1
}
if (Get-Process Plus365 -ErrorAction SilentlyContinue) {
    throw "The app is still running after ${deadline}s. Close it and try again."
}

Write-Host "Building the installer..."
& .\gradlew.bat :desktop:packageMsi --offline
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "packageMsi failed" }

$msi = Get-ChildItem "$repo\desktop\build\compose\binaries\main\msi\*.msi" |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
Write-Host "Installing $($msi.Name)"

$log = Join-Path $env:TEMP "plus365-install.log"

# Uninstall first, then install clean.
#
# Reinstalling over the same version is the normal case during development, and
# Windows fights it: a plain /i returns 1638 ("another version is already
# installed"), and REINSTALL=ALL REINSTALLMODE=vomus gets as far as 1603, a bare
# "fatal error". Removing the old product first costs a few seconds and behaves
# the same every time, which is worth more than the seconds.
$uninstallKeys = @(
    "HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*",
    "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*"
)
$installed = Get-ItemProperty $uninstallKeys -ErrorAction SilentlyContinue |
    Where-Object { $_.DisplayName -eq "Plus365" }

foreach ($prod in $installed) {
    Write-Host "Removing the installed $($prod.DisplayName) $($prod.DisplayVersion)"
    $u = Start-Process msiexec.exe -Wait -PassThru -ArgumentList @(
        "/x", $prod.PSChildName, "/qn", "/norestart"
    )
    if ($u.ExitCode -ne 0) { Write-Host "  (uninstall returned $($u.ExitCode), continuing)" }
}

$p = Start-Process msiexec.exe -Wait -PassThru -ArgumentList @(
    "/i", "`"$($msi.FullName)`"", "/qn", "/norestart", "/l*v", "`"$log`""
)
if ($p.ExitCode -ne 0) {
    Write-Host "msiexec failed with $($p.ExitCode). Last lines of $log :"
    Get-Content $log -Tail 20
    Pop-Location
    throw "install failed"
}

# Verify rather than assume. An install that silently did not replace the old one
# looks exactly like code that silently did not work, and that has bitten here.
$exe = "$env:LOCALAPPDATA\Plus365\Plus365.exe"
if (-not (Test-Path $exe)) { Pop-Location; throw "installed exe not found at $exe" }
$stamp = (Get-Item $exe).LastWriteTime
Write-Host ""
Write-Host "Installed : $exe"
Write-Host "Built     : $stamp"
Write-Host "Start menu: $env:APPDATA\Microsoft\Windows\Start Menu\Programs\365+\Plus365.lnk"
Write-Host ""
Write-Host "Launching. Check the build line under the title matches what you expect."
Start-Process $exe
Pop-Location
