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
# `taskkill /F /T` rather than Stop-Process: the jpackage launcher spawns a child
# of the same name, and killing the tree is the only way to be sure both go. Ten
# seconds was not always enough — a process that has just started can take longer
# than that to go down — so this waits thirty and says which pid it is waiting on.
$deadline = 30
for ($i = 0; $i -lt $deadline; $i++) {
    $running = @(Get-Process Plus365 -ErrorAction SilentlyContinue)
    if ($running.Count -eq 0) { break }
    foreach ($p in $running) {
        if ($i -eq 0) { Write-Host "Closing the running app (pid $($p.Id))" }
        # Stderr stays inside cmd. Redirecting a native command's stderr in
        # PowerShell wraps each line in an ErrorRecord, which with
        # $ErrorActionPreference = "Stop" kills the script — so a process that
        # had already exited between the listing and the kill would fail an
        # install that was about to succeed.
        cmd /c "taskkill /F /T /PID $($p.Id) >nul 2>&1"
    }
    Start-Sleep -Seconds 1
}
$stuck = @(Get-Process Plus365 -ErrorAction SilentlyContinue)
if ($stuck.Count -gt 0) {
    throw "Still running after ${deadline}s (pid $($stuck.Id -join ', ')). Close it and try again."
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

# Ask the running app what it is, rather than trusting that the install took.
#
# This exists because it did not, once. The app was running while the MSI
# installed; Windows replaced the launcher and kept the old jars, and the thing
# that came back up reported the previous version while every step above had
# printed success. An install that silently half-applies looks exactly like a
# feature that silently does not work, and the whole point of a version stamp is
# lost if nobody checks it.
#
# So the script now reads the stamp back off the endpoint and fails loudly when
# it disagrees with the source it just built from.
$want = (Select-String -Path "$repo/core/src/commonMain/kotlin/online/vyybandasky/plus365/core/BuildInfo.kt" `
    -Pattern 'const val NAME: String = "([^"]+)"').Matches[0].Groups[1].Value

$got = $null
foreach ($try in 1..20) {
    Start-Sleep -Seconds 2
    foreach ($port in 8443, 8543, 8643, 9443) {
        try {
            $r = Invoke-RestMethod -Uri "http://127.0.0.1:$port/health" -TimeoutSec 3
            if ($r.version) { $got = $r; break }
        } catch { }
    }
    if ($got) { break }
}

if (-not $got) {
    Write-Host ""
    Write-Host "Could not reach the app to check its version." -ForegroundColor Yellow
    Write-Host "It may still be starting, or every candidate port is taken."
} elseif ($got.version -ne $want) {
    Write-Host ""
    Write-Host "STALE INSTALL: the app reports $($got.version) but this build is $want." -ForegroundColor Red
    Write-Host "Close every copy of 365+ and run this again."
    throw "installed version does not match the build"
} else {
    Write-Host ""
    Write-Host "Verified: running $($got.version) (commit $($got.commit))." -ForegroundColor Green
}
Pop-Location
