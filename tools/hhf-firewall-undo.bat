@echo off
setlocal EnableExtensions
title HaleHound Fire — Undo HHF firewall rules
net session >nul 2>&1
if errorlevel 1 (
  echo [!] Run as Administrator
  pause
  exit /b 1
)
echo Removing HHF baseline rules...
netsh advfirewall firewall delete rule name="HHF-BLOCK Telnet TCP-23" >nul 2>&1
netsh advfirewall firewall delete rule name="HHF-BLOCK FTP TCP-21" >nul 2>&1
netsh advfirewall firewall delete rule name="HHF-BLOCK SMB TCP-445" >nul 2>&1
netsh advfirewall firewall delete rule name="HHF-BLOCK NetBIOS TCP-139" >nul 2>&1
netsh advfirewall firewall delete rule name="HHF-BLOCK NetBIOS TCP-137-138" >nul 2>&1
netsh advfirewall firewall delete rule name="HHF-BLOCK NetBIOS UDP-137-139" >nul 2>&1
netsh advfirewall firewall delete rule name="HHF-BLOCK SMB UDP-445" >nul 2>&1
netsh advfirewall firewall delete rule name="HHF-BLOCK RDP TCP-3389" >nul 2>&1
netsh advfirewall firewall delete rule name="HHF-BLOCK VNC TCP-5900" >nul 2>&1
netsh advfirewall firewall delete rule name="HHF-BLOCK RPC TCP-135" >nul 2>&1
echo Done. Delete any HHF-AUDIT* rules from firewall MMC if needed.
pause
endlocal
