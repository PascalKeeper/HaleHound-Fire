# HaleHound Fire — Windows Guardian Backend

Virtual backend that runs on your **Windows PC** with **Npcap + Scapy** so the Fire tablet can show **real (or virtual) deauth detections**.

```
[ Wi‑Fi RF ] → Npcap/Scapy (PC) → HTTP :8765 → HaleHound Fire (tablet)
```

## Quick start

1. Install [Npcap](https://npcap.com/) if missing (`wpcap.dll` in System32).
2. Double‑click `start-backend.bat` (or run as Admin for best sniffer results).
3. Note the printed **Fire URL**, e.g. `http://172.16.19.136:8765`
4. On the Fire app → **GUARD** → set **Backend URL** → **CONNECT**

## Modes

| Mode | When | What |
|------|------|------|
| `sniff` | Npcap + Scapy + openable iface | Real `Dot11Deauth` frames |
| `virtual` | Default start script / no RF | Synthetic alerts so pipeline works |
| `limited` | Partial install | API up, no frames |

`start-backend.bat` uses `--virtual` so the Fire always receives radar events while you validate the link. Use `start-backend-sniff.bat` for sniffer-first (still falls back if capture fails).

## API

- `GET /api/v1/health`
- `GET /api/v1/status`
- `GET /api/v1/alerts?limit=50`
- `GET /api/v1/stream`
- `POST /api/v1/simulate` `{"source":"aa:bb:..","dest":"ff:ff:..","reason":7}`

## Ethics

**Blue Team / authorized detection only.** This backend does **not** transmit deauth frames. Use only on networks you own or have written permission to monitor.
