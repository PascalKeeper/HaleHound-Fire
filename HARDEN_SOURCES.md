# Harden arsenal — sources scraped from this machine

Knowledge and port policy ported into `HardeningKnowledge` / `HardeningEngine` for **on-device** Fire use.

| Source file | What we took |
|-------------|--------------|
| `Desktop\Secure Firewall Ports.bat` | Block list: 23, 21, 137–139, 445, 3389, 5900 |
| `Documents\secure_gaming_firewall.ps1` | RDP/Telnet/SMB/NetBIOS blocks; camera IP:port pattern; app allow notes |
| `Documents\ultimate_net_optimizer.bat` | DNS → Cloudflare 1.1.1.1; flush/reset playbook; latency mindset |
| `Documents\HardenServices.ps1` | UPnP / remote access / telemetry *concepts* for router checklist |
| `SecuritySentinel_Pro.ps1` | RDP trusted-IP model; SMB share discipline |
| `SecurityMonitor.ps1` | Unauthorized remote access awareness |
| `Project PERSEUS … SecurityAutomation.py` | HARDENING_MANIFEST structure (firewall, SMB, RDP, Telnet, flush DNS) |
| `WifiGuard.ps1` / `WiFiGuardianKiller.ps1` | SSID lock + reconnect (GUARD tab) |
| `NetworkMonitor_v2` | PMF / 5 GHz mitigation language |

## Fire OS limits (honest)

We **cannot** push Windows `netsh` firewall rules from the tablet.  
We **can** audit Wi‑Fi posture, probe danger ports on gateway/LAN hosts, score, checklist, and open system DNS/Wi‑Fi settings.

True WAN firewall remains on the router/PC; Fire is the **field auditor + operator brain**.
