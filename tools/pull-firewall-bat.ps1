# Pull Fire-generated HHF_firewall_harden.bat and offer to run elevated
$ErrorActionPreference = "Stop"
$Adb = if (Test-Path "F:\Android\Sdk\platform-tools\adb.exe") {
    "F:\Android\Sdk\platform-tools\adb.exe"
} else { "adb" }

$pkg = "com.halehoundforge.fire.debug"
# App external files path pattern
$remote = "/storage/emulated/0/Android/data/$pkg/files/HHF_firewall_harden.bat"
$outDir = Join-Path $PSScriptRoot "pulled"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$local = Join-Path $outDir "HHF_firewall_harden.bat"

Write-Host "Pulling $remote ..."
& $Adb pull $remote $local
if (-not (Test-Path $local)) {
    # fallback run-as for some Fire builds
    Write-Host "Direct pull failed — try after GEN on Fire. Path may vary."
    exit 1
}
Write-Host "Saved: $local"
Write-Host "Right-click → Run as administrator  (or:)"
Write-Host "  Start-Process -FilePath `"$local`" -Verb RunAs"
