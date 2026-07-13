# Capture HaleHound Fire ship screenshots from a plugged Fire tablet (or any Android device).
# Usage:  .\tools\capture-ship-screens.ps1
# Needs: adb, USB debugging, app installed (debug package).

$ErrorActionPreference = "Stop"
$Pkg = "com.halehoundforge.fire.debug"
$Activity = "com.halehoundforge.fire.ui.ValhallaGateActivity"
$OutDir = Join-Path (Split-Path $PSScriptRoot -Parent) "docs\screenshots"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Require-Device {
    $lines = adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "device$" }
    if (-not $lines) {
        throw "No adb device. Plug Fire tablet, enable USB debugging, unlock screen."
    }
    Write-Host "Device OK: $($lines -join ', ')"
}

function Screencap([string]$name) {
    $remote = "/sdcard/hhf_cap.png"
    $local = Join-Path $OutDir $name
    adb shell screencap -p $remote | Out-Null
    adb pull $remote $local | Out-Null
    adb shell rm $remote 2>$null | Out-Null
    if (-not (Test-Path $local) -or (Get-Item $local).Length -lt 1000) {
        throw "Screencap failed: $name"
    }
    Write-Host "  wrote $name ($([int]((Get-Item $local).Length/1KB)) KB)"
}

function TapPercent([double]$xp, [double]$yp) {
    # Fire 7 ~600x1024 portrait-ish; use wm size
    $sz = (adb shell wm size) -replace ".*Physical size:\s*", ""
    $w, $h = $sz.Trim().Split("x") | ForEach-Object { [int]$_ }
    $x = [int]($w * $xp)
    $y = [int]($h * $yp)
    adb shell input tap $x $y | Out-Null
    Start-Sleep -Milliseconds 700
}

Require-Device

Write-Host "Launching app…"
adb shell am force-stop $Pkg 2>$null | Out-Null
adb shell am start -n "$Pkg/$Activity" | Out-Null
Start-Sleep -Seconds 2

# VALHALLA: ACCEPT is usually lower third center-left-ish
Write-Host "Accept VALHALLA if shown…"
TapPercent 0.35 0.82
Start-Sleep -Seconds 1
TapPercent 0.50 0.78
Start-Sleep -Seconds 1

# Bottom nav: 5 tabs ARSENAL HARDEN GUARD TERM CYD  — y ~0.93, x ~0.10 0.30 0.50 0.70 0.90
$tabs = @(
    @{ x = 0.10; file = "01-arsenal.png";  label = "ARSENAL" },
    @{ x = 0.30; file = "02-harden.png";   label = "HARDEN" },
    @{ x = 0.50; file = "03-guard.png";    label = "GUARD" },
    @{ x = 0.70; file = "04-term.png";     label = "TERM" },
    @{ x = 0.90; file = "05-cyd.png";      label = "CYD" }
)

foreach ($t in $tabs) {
    Write-Host "Tab $($t.label)…"
    TapPercent $t.x 0.93
    Start-Sleep -Seconds 1
    # GUARD needs a moment for first snapshot
    if ($t.label -eq "GUARD") { Start-Sleep -Seconds 2 }
    if ($t.label -eq "TERM") {
        # tap HELP chip area roughly
        TapPercent 0.12 0.18
        Start-Sleep -Seconds 1
    }
    Screencap $t.file
}

# About from arsenal about tile if visible
Write-Host "About (best-effort)…"
TapPercent 0.10 0.93
Start-Sleep -Milliseconds 500
TapPercent 0.75 0.42
Start-Sleep -Seconds 1
Screencap "06-about.png"

Write-Host ""
Write-Host "Done. Screenshots in: $OutDir"
Get-ChildItem $OutDir -Filter "*.png" | Format-Table Name, Length -AutoSize
