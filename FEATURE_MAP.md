# Feature map → HaleHound Fire

Sources audited on this machine and how they map into the Fire OS app.

## Sources found

| Path | What it is |
|------|------------|
| `F:\imonlinegaming\NetworkMonitor_v2\core_monitor.py` | DevShe Network Monitor v2 — Rich TUI, bandwidth, AIOps latency/jitter, Scapy deauth radar, process map, hex dissect |
| `F:\imonlinegaming\NetworkMonitor_v2\F_Drive_NetworkMonitor_Installer_v2.py` | Installer + Npcap bootstrap |
| `C:\Users\imonlinegaming\Documents\WiFiGuardianKiller.ps1` | Auto-reconnect loop when Wi‑Fi drops (deauth mitigation heuristic) |
| `C:\Users\imonlinegaming\WifiGuard.ps1` | Force stick to a preferred SSID |
| `C:\Users\imonlinegaming\PerseusNetworkSentinel\` | WPF shell (monitor service stub) |
| `C:\Users\imonlinegaming\SecurityMonitor.ps1` | RDP/SMB host hardening (Windows-only, not ported) |
| Official HaleHound CYD | **WiFi Guardian** = defensive deauth flood detect (needs monitor radio) |

No separate folder literally named “Hale Network Guardian” was found. Closest local builds are **NetworkMonitor_v2** + **WiFiGuardianKiller**; official product concept is HaleHound CYD **Jam Detect → WiFi Guardian**.

## Port matrix (Fire OS stock)

| Feature | Source | Fire port | Notes |
|---------|--------|-----------|-------|
| Live TX/RX speeds | NetworkMonitor bandwidth | **YES** — `TrafficStats` | System-wide Mbps |
| Gateway latency / jitter / drops | NetworkMonitor predictive | **YES** — TCP probe gw:80/53 | No raw ICMP required |
| Predictive status + mitigation text | NetworkMonitor AIOps | **YES** | Same severity ladder (STABLE→CRITICAL) |
| Disconnect storm / “deauth radar” | NetworkMonitor Dot11Deauth + WiFiGuardianKiller | **HEURISTIC** | Supplicant/network lost events; **not** true 802.11 management frames |
| SSID lock | WifiGuard.ps1 | **YES** | Preferred SSID compare + alert |
| Force reconnect | WiFiGuardianKiller.ps1 | **YES** | `enableNetwork` + `reconnect` if SSID saved |
| Monitor mode / Npcap / Scapy | NetworkMonitor | **NO** | Windows-only |
| Per-process connection map | NetworkMonitor GlassWire | **NO** | No Android equivalent without root/VPN |
| Live hex packet dump | NetworkMonitor Wireshark mode | **NO** | Needs raw capture |
| True deauth frame count | HaleHound CYD WiFi Guardian | **NO on Fire** | Point users at CYD for real frames |
| RDP/SMB share killer | SecurityMonitor.ps1 | **NO** | Desktop host hardening only |

## CYD companion (v1.1.0+)

| Feature | Fire surface | Notes |
|---------|--------------|-------|
| Live HTTP telemetry | CYD tab + `cyd status` | SoftAP often `192.168.4.1`; path probe + latency |
| Loot hints scrape | CYD tab + `cyd loot` | JSON/HTML/filename best-effort |
| Loot offload vault | PULL LOOT + `cyd pull` | `Android/data/.../cyd_loot/`; optional `.hhf` encrypt |
| Vault list | CYD tab + `cyd vault` | Local sessions only — no phone-home |
| SoftAP discover | `cyd discover` | Quick host scan |

Private LAN/softAP always allowed. Public hosts only via HomeCallPolicy (HTTPS sensei path).

## Ethics

All Fire-side Guardian features are **defensive / Blue Team**. No deauth transmission. True frame-level Guardian remains on authorized ESP32 CYD hardware.

Kitchen secrets / feature backlog: see **KITCHEN_SECRETS.md** (no withholding rule).
