#Requires -Version 5.1
<#
.SYNOPSIS
  Grok Build / human helper for HaleHound Fire on-device ops.
  Behind-the-scenes family tooling — install, launch, log, doctor.

.EXAMPLE
  .\tools\grok-fire-ops.ps1 doctor
  .\tools\grok-fire-ops.ps1 install
  .\tools\grok-fire-ops.ps1 launch
  .\tools\grok-fire-ops.ps1 log
  .\tools\grok-fire-ops.ps1 term   # open in-app terminal via adb (best-effort)
#>
param(
    [Parameter(Position = 0)]
    [ValidateSet("doctor", "install", "build", "launch", "log", "devices", "term", "help")]
    [string]$Action = "help"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
if (-not (Test-Path (Join-Path $Root "app"))) {
    $Root = Split-Path -Parent $MyInvocation.MyCommand.Path
    if (Test-Path (Join-Path (Split-Path $Root) "app")) { $Root = Split-Path $Root }
}

$AdbCandidates = @(
    "F:\Android\Sdk\platform-tools\adb.exe",
    "$env:ANDROID_HOME\platform-tools\adb.exe",
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    "adb"
)
$Adb = $AdbCandidates | Where-Object {
    $_ -eq "adb" -or (Test-Path $_)
} | Select-Object -First 1

function Invoke-Adb([string[]]$Args) {
    if (-not $Adb) { throw "adb not found" }
    if ($Adb -eq "adb") { & adb @Args } else { & $Adb @Args }
}

function Show-Help {
    @"
HaleHound Fire — Grok Ops Helper
  doctor   check adb, device, package
  build    gradle assembleDebug
  install  install debug APK
  launch   start app (Valhalla/Main)
  term     launch app (open TERM manually or type open on device)
  log      tail logcat for halehound
  devices  adb devices -l
  help     this text

On-device terminal commands (agent protocol):
  help | agent | status | harden | wifi | cyd | ports <ip> | ping <host>
"@
}

switch ($Action) {
    "help" { Show-Help; break }
    "devices" {
        Invoke-Adb @("devices", "-l")
        break
    }
    "doctor" {
        Write-Host "ROOT  $Root"
        Write-Host "ADB   $Adb"
        Invoke-Adb @("version")
        Invoke-Adb @("devices", "-l")
        $pkg = "com.halehoundforge.fire.debug"
        Invoke-Adb @("shell", "pm", "path", $pkg)
        Write-Host "OK doctor complete"
        break
    }
    "build" {
        Push-Location $Root
        try {
            $env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "F:\tools\jdk-17" }
            $env:ANDROID_HOME = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "F:\Android\Sdk" }
            .\gradlew.bat :app:assembleDebug
        } finally { Pop-Location }
        break
    }
    "install" {
        $apk = Join-Path $Root "app\build\outputs\apk\debug\app-debug.apk"
        if (-not (Test-Path $apk)) {
            Write-Host "APK missing — building…"
            & $PSCommandPath build
        }
        Invoke-Adb @("install", "-r", $apk)
        Write-Host "Installed $apk"
        break
    }
    "launch" {
        Invoke-Adb @(
            "shell", "am", "start", "-n",
            "com.halehoundforge.fire.debug/com.halehoundforge.fire.ui.ValhallaGateActivity"
        )
        break
    }
    "term" {
        Invoke-Adb @(
            "shell", "am", "start", "-n",
            "com.halehoundforge.fire.debug/com.halehoundforge.fire.ui.ValhallaGateActivity"
        )
        Write-Host "App launched. On device: accept VALHALLA if needed → TERM tab."
        Write-Host "Or arsenal path: open terminal chips → help / harden / agent"
        break
    }
    "log" {
        Invoke-Adb @("logcat", "-c") | Out-Null
        Write-Host "Streaming logcat (Ctrl+C to stop)…"
        Invoke-Adb @("logcat", "*:S", "AndroidRuntime:E", "halehound:V", "System.err:W")
        break
    }
}
