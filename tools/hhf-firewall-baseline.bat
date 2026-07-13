@echo off
REM Fallback PC copy of Fire-generated baseline (same rules).
REM Prefer the .bat generated on the Fire after an audit for audit-hit ports.
setlocal EnableExtensions
title HaleHound Fire — Baseline Windows Firewall Harden
color 0A
echo ============================================================
echo   HALEHOUND-FIRE  WINDOWS FIREWALL HARDEN (PC baseline)
echo   Blue Team / authorized host only
echo ============================================================
echo.
net session >nul 2>&1
if errorlevel 1 (
  echo [!] Run as Administrator.
  pause
  exit /b 1
)
echo [+] Admin OK — applying inbound block rules...
echo.

call :BLOCK "HHF-BLOCK Telnet TCP-23" TCP 23 "cleartext remote"
call :BLOCK "HHF-BLOCK FTP TCP-21" TCP 21 "legacy FTP"
call :BLOCK "HHF-BLOCK SMB TCP-445" TCP 445 "Anti-WannaCry SMB"
call :BLOCK "HHF-BLOCK NetBIOS TCP-139" TCP 139 "NetBIOS session"
call :BLOCK "HHF-BLOCK NetBIOS TCP-137-138" TCP 137-138 "NetBIOS name/datagram"
call :BLOCK "HHF-BLOCK NetBIOS UDP-137-139" UDP 137-139 "NetBIOS UDP"
call :BLOCK "HHF-BLOCK SMB UDP-445" UDP 445 "SMB UDP"
call :BLOCK "HHF-BLOCK RDP TCP-3389" TCP 3389 "RDP ransomware vector"
call :BLOCK "HHF-BLOCK VNC TCP-5900" TCP 5900 "VNC remote"
call :BLOCK "HHF-BLOCK RPC TCP-135" TCP 135 "Windows RPC"

echo.
echo [SUCCESS] Baseline HHF rules applied.
echo Undo: tools\hhf-firewall-undo.bat
pause
endlocal
exit /b 0

:BLOCK
set "RNAME=%~1"
set "RPROTO=%~2"
set "RPORTS=%~3"
set "RWHY=%~4"
echo [!] BLOCK %RPROTO% %RPORTS%  (%RWHY%)
netsh advfirewall firewall delete rule name="%RNAME%" >nul 2>&1
netsh advfirewall firewall add rule name="%RNAME%" dir=in action=block protocol=%RPROTO% localport=%RPORTS% enable=yes profile=any description="HaleHound Fire harden · %RWHY%"
if errorlevel 1 (echo     [x] failed: %RNAME%) else (echo     [+] ok: %RNAME%)
echo.
goto :eof
