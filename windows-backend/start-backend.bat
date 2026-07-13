@echo off
setlocal EnableExtensions
cd /d "%~dp0"

echo ============================================================
echo  HaleHound Fire — Guardian Backend (Npcap / Scapy)
echo  Blue Team deauth radar for the Fire tablet
echo ============================================================

where python >nul 2>&1
if errorlevel 1 (
  echo [!] python not in PATH
  exit /b 1
)

echo [*] Installing / verifying dependencies...
python -m pip install --upgrade -q -r requirements.txt
if errorlevel 1 (
  echo [!] pip install failed
  exit /b 1
)

echo [*] Starting backend on 0.0.0.0:8765
echo [*] Keep this window open.
echo.
echo     Fire via USB (recommended):
echo       adb reverse tcp:8765 tcp:8765
echo       Backend URL on tablet: http://127.0.0.1:8765
echo.
echo     Fire via Wi-Fi (same LAN, no AP isolation):
echo       http://^<this-PC-LAN-IP^>:8765
echo.

where adb >nul 2>&1
if not errorlevel 1 (
  adb reverse tcp:8765 tcp:8765 >nul 2>&1
  echo [*] adb reverse tcp:8765 attempted
)

REM Prefer real sniff when possible; --virtual keeps pipeline alive if RF fails
python guardian_backend.py --host 0.0.0.0 --port 8765 --virtual %*
endlocal
