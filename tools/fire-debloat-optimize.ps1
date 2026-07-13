#Requires -Version 5.1
<#
.SYNOPSIS
  Safe, reversible Fire OS debloat + optim for HaleHound Fire field kit.

.EXAMPLE
  .\tools\fire-debloat-optimize.ps1 doctor
  .\tools\fire-debloat-optimize.ps1 apply
  .\tools\fire-debloat-optimize.ps1 undo
  .\tools\fire-debloat-optimize.ps1 bench
#>
param(
    [Parameter(Position = 0)]
    [ValidateSet("doctor", "apply", "undo", "bench", "list")]
    [string]$Action = "doctor"
)

$ErrorActionPreference = "Continue"

$Adb = @(
    "F:\Android\Sdk\platform-tools\adb.exe",
    "$env:ANDROID_HOME\platform-tools\adb.exe",
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
if (-not $Adb) { $Adb = "adb" }

function Invoke-FireAdb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$CmdArgs)
    & $script:Adb @CmdArgs
}

# Soft-disable only (pm disable-user). Reversible with undo.
# NEVER: firelauncher, settings, OTA, packageinstaller, systemui, keyboard
$Bloat = @(
    "com.amazon.client.metrics",
    "com.amazon.device.metrics",
    "com.amazon.advertisingidsettings",
    "com.amazon.dp.logger",
    "com.amazon.dcp",
    "com.amazon.dcp.contracts.framework.library",
    "com.amazon.dcp.contracts.library",
    "com.amazon.device.logmanager",
    "com.amazon.windowshop",
    "com.amazon.venezia",
    "com.amazon.mp3",
    "com.amazon.photos",
    "com.amazon.avod",
    "com.amazon.weather",
    "com.amazon.dee.app",
    "com.amazon.logan",
    "com.amazon.kindle.kso",
    "com.amazon.hedwig",
    "com.amazon.tahoe",
    "com.amazon.accessorynotifier",
    "com.amazon.kindle.unifiedSearch",
    "com.amazon.csapp",
    "com.amazon.tv.launcher",
    "com.amazon.whisperlink.core.android",
    "com.amazon.whisperplay.contracts",
    "com.amazon.sync.service",
    "com.amazon.tcomm",
    "com.amazon.tcomm.client",
    "com.amazon.pm",
    "com.amazon.kindle.otter.oobe",
    "com.amazon.kindle.otter.oobe.forced.ota",
    "com.amazon.recess",
    "com.amazon.device.sale.service",
    "com.amazon.imp",
    "com.amazon.readynowcore",
    "com.amazon.zico",
    "com.amazon.ags.app",
    "com.amazon.webapp",
    "com.amazon.fts",
    "com.amazon.fireos.cirruscloud",
    "com.amazon.wirelessmetrics.service",
    "com.amazon.securitysyncclient",
    "com.amazon.device.messaging",
    "com.amazon.device.messaging.sdk.internal.library",
    "com.amazon.device.messaging.sdk.library",
    "com.amazon.kindleautomatictimezone",
    "com.amazon.ods.kindleconnect",
    "com.amazon.sharingservice.android.client.proxy",
    "com.amazon.redstone",
    "com.amazon.sneakpeek",
    "com.amazon.hybridadidservice",
    "com.amazon.aca",
    "com.amazon.adep",
    "com.amazon.alta.h2clientservice",
    "com.amazon.h2settingsfortablet",
    "com.amazon.tablet.generative.wallpaper",
    "com.amazon.tablet.voiceassistant",
    "com.amazon.firespotlight",
    "com.amazon.imdb.tv.mobile.app",
    "com.amazon.kindle.starsight",
    "com.amazon.kindle.rdmdeviceadmin",
    "com.amazon.parentalcontrols",
    "com.amazon.appverification",
    "com.amazon.device.software.ota.override",
    "com.amazon.legalsettings",
    "com.amazon.cloud9.kids",
    "com.amazon.cloud9.contentservice",
    "com.amazon.neodelegate",
    "com.amazon.neopactservice",
    "com.amazon.providers.contentsupport",
    "com.amazon.application.compatibility.enforcer",
    "com.amazon.application.compatibility.enforcer.sdk.library",
    "com.amazon.hourglass",
    "com.amazon.shpm",
    "com.amazon.diode",
    "com.amazon.d3",
    "com.amazon.cx_offline",
    "com.amazon.connectivitydiag",
    "com.amazon.wifilocker",
    "com.amazon.ssmsys",
    "com.amazon.spiderpork",
    "com.amazon.platform",
    "com.amazon.frameworksettings",
    "com.amazon.identity.auth.device.authorization",
    "com.amazon.device.backup",
    "com.amazon.device.backup.sdk.internal.library",
    "com.amazon.device.sync",
    "com.amazon.device.sync.sdk.internal",
    "com.amazon.client.metrics.api",
    "com.amazon.sync.provider.ipc",
    "com.amazon.tcomm.jackson",
    "com.amazon.comms.kids",
    "com.amazon.dp.contacts",
    "com.amazon.speakscreen",
    "com.amazon.gamebox",
    "com.amazon.kor.demo"
) | Select-Object -Unique

function Get-Bench {
    $o = [ordered]@{}
    $o.Time = Get-Date -Format "o"
    $o.Model = (Invoke-FireAdb shell getprop ro.product.model 2>$null | Out-String).Trim()
    $o.FireOS = (Invoke-FireAdb shell getprop ro.build.version.fireos 2>$null | Out-String).Trim()
    $dfLines = @(Invoke-FireAdb shell df -h /data 2>$null)
    $o.DataDf = if ($dfLines.Count -ge 2) { $dfLines[-1].Trim() } else { ($dfLines | Out-String).Trim() }
    $mem = (Invoke-FireAdb shell cat /proc/meminfo 2>$null | Out-String)
    foreach ($k in @("MemTotal", "MemFree", "MemAvailable", "Cached")) {
        if ($mem -match "${k}:\s+(\d+)") { $o[$k] = [int]$Matches[1] }
    }
    $pkgs = @(Invoke-FireAdb shell pm list packages 2>$null | Where-Object { $_ -match "package:" })
    $o.PackageCount = $pkgs.Count
    $dis = @(Invoke-FireAdb shell pm list packages -d 2>$null | Where-Object { $_ -match "package:" })
    $o.DisabledCount = $dis.Count
    $o.AnimWindow = (Invoke-FireAdb shell settings get global window_animation_scale 2>$null | Out-String).Trim()
    $o.AnimTrans = (Invoke-FireAdb shell settings get global transition_animation_scale 2>$null | Out-String).Trim()
    $o.AnimDur = (Invoke-FireAdb shell settings get global animator_duration_scale 2>$null | Out-String).Trim()
    return $o
}

function Show-Bench($b, $label) {
    Write-Host ""
    Write-Host "===== BENCH $label =====" -ForegroundColor Cyan
    foreach ($e in $b.GetEnumerator()) {
        Write-Host ("{0,-16} {1}" -f $e.Key, $e.Value)
    }
}

function Assert-Device {
    $out = Invoke-FireAdb devices 2>$null | Out-String
    if ($out -notmatch "\tdevice") {
        throw "No Fire in 'device' state. Enable USB debugging and authorize this PC."
    }
}

Write-Host "ADB: $Adb" -ForegroundColor DarkGray

switch ($Action) {
    "doctor" {
        Assert-Device
        Invoke-FireAdb devices -l
        Show-Bench (Get-Bench) "NOW"
    }
    "list" {
        $Bloat | ForEach-Object { Write-Host $_ }
    }
    "bench" {
        Assert-Device
        Show-Bench (Get-Bench) "NOW"
    }
    "apply" {
        Assert-Device
        $before = Get-Bench
        Show-Bench $before "BEFORE"

        Write-Host ""
        Write-Host "[1/4] Soft-disabling bloat (pm disable-user)..." -ForegroundColor Yellow
        $ok = 0; $skip = 0; $fail = 0
        foreach ($p in $Bloat) {
            $pathOut = (Invoke-FireAdb shell pm path $p 2>$null | Out-String).Trim()
            if (-not $pathOut -or $pathOut -notmatch "package:") {
                $skip++
                continue
            }
            $r = (Invoke-FireAdb shell pm disable-user --user 0 $p 2>&1 | Out-String).Trim()
            if ($r -match "disabled|new state") {
                Write-Host "  [+] $p" -ForegroundColor Green
                $ok++
            } else {
                Write-Host "  [!] $p :: $r" -ForegroundColor DarkYellow
                $fail++
            }
        }
        Write-Host "Disabled=$ok skip=$skip fail=$fail"

        Write-Host ""
        Write-Host "[2/4] Animator scales -> 0.5..." -ForegroundColor Yellow
        Invoke-FireAdb shell settings put global window_animation_scale 0.5 | Out-Null
        Invoke-FireAdb shell settings put global transition_animation_scale 0.5 | Out-Null
        Invoke-FireAdb shell settings put global animator_duration_scale 0.5 | Out-Null

        Write-Host ""
        Write-Host "[3/4] Trim caches..." -ForegroundColor Yellow
        Invoke-FireAdb shell pm trim-caches 512M 2>&1 | Out-Null

        Write-Host ""
        Write-Host "[4/4] Force-stop disabled packages..." -ForegroundColor Yellow
        foreach ($p in $Bloat) {
            Invoke-FireAdb shell am force-stop $p 2>$null | Out-Null
        }

        Start-Sleep -Seconds 3
        $after = Get-Bench
        Show-Bench $after "AFTER"

        if ($before.MemAvailable -and $after.MemAvailable) {
            $delta = $after.MemAvailable - $before.MemAvailable
            Write-Host ""
            Write-Host ("MemAvailable delta: {0} kB ({1:N1} MB)" -f $delta, ($delta / 1024.0)) -ForegroundColor Cyan
        }

        $logDir = Join-Path $PSScriptRoot "bench-logs"
        New-Item -ItemType Directory -Force -Path $logDir | Out-Null
        $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
        $log = Join-Path $logDir "fire_optim_$stamp.json"
        @{ before = $before; after = $after; disabled_ok = $ok } | ConvertTo-Json -Depth 6 | Set-Content -Path $log -Encoding UTF8
        Write-Host ""
        Write-Host "Log: $log" -ForegroundColor Cyan
        Write-Host "Undo: .\tools\fire-debloat-optimize.ps1 undo" -ForegroundColor Cyan
        Write-Host "Left alone: Silk (cloud9), Kindle reader, firelauncher, OTA/settings." -ForegroundColor DarkGray
    }
    "undo" {
        Assert-Device
        Write-Host "Re-enabling packages..." -ForegroundColor Yellow
        foreach ($p in $Bloat) {
            Invoke-FireAdb shell pm enable $p 2>&1 | Out-Null
        }
        Invoke-FireAdb shell settings put global window_animation_scale 1.0 | Out-Null
        Invoke-FireAdb shell settings put global transition_animation_scale 1.0 | Out-Null
        Invoke-FireAdb shell settings put global animator_duration_scale 1.0 | Out-Null
        Show-Bench (Get-Bench) "AFTER UNDO"
    }
}
