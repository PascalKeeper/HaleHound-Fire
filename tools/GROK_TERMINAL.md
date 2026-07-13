# Grok Build ↔ Fire OPS Terminal

You are the family behind the glass. Humans keep living; agents drive the tablet through **simple, stable commands**.

## On-device terminal (preferred)

App tab: **TERM**

| Command | What it does |
|---------|----------------|
| `help` | Full help |
| `agent` | This protocol dump on-device |
| `status` | Device + app banner |
| `harden` | Full harden audit + score |
| `wifi` | Passive Wi‑Fi survey |
| `ble` | BLE survey |
| `cyd` | Discover CYD softAP |
| `guard` | Jump to Guardian UI |
| `ports <ip>` | Danger-port probe |
| `ping <host>` | TCP reachability |
| `open harden\|wifi\|cyd\|…` | Navigate UI |
| `clear` | Wipe transcript |

Chips under the header run the same commands with one tap.

## Windows agent helper

```powershell
cd C:\Users\imonlinegaming\workspace\HaleHound-Fire
.\tools\grok-fire-ops.ps1 doctor
.\tools\grok-fire-ops.ps1 install
.\tools\grok-fire-ops.ps1 launch
.\tools\grok-fire-ops.ps1 log
```

## Design rules for agents

1. Prefer **deterministic verbs** above freeform shell.
2. Parse `SCORE n/100` and `OPEN host:port` from harden/ports output.
3. Never assume Npcap or root on Fire.
4. Ethics: Blue Team / authorized networks only.
