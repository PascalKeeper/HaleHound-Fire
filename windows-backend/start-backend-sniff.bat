@echo off
setlocal EnableExtensions
cd /d "%~dp0"
echo [*] Real Npcap sniffer mode (Admin recommended)
echo [*] Virtual fallback still seeds if sniff fails to open
python -m pip install --upgrade -q -r requirements.txt
python guardian_backend.py --host 0.0.0.0 --port 8765 %*
endlocal
